package io.github.thomaswcode.privacyscreenblackout.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec

/** Full-screen, non-touchable view that draws the active mask. */
class MaskView(context: Context) : View(context) {

    private var spec: MaskSpec? = null
    private var paths: List<Path> = emptyList()
    private var color = 0xFF000000.toInt()
    private var alpha255 = 255

    fun setModel(spec: MaskSpec?, color: Int, opacity: Float) {
        if (spec == this.spec && color == this.color && alpha255 == toAlpha(opacity)) return
        this.spec = spec
        this.paths = spec?.shapes?.map(MaskRenderer::pathFor) ?: emptyList()
        this.color = color
        this.alpha255 = toAlpha(opacity)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = spec ?: return
        MaskRenderer.draw(canvas, s, color, alpha255, paths)
    }

    private fun toAlpha(opacity: Float) = (opacity.coerceIn(0f, 1f) * 255).toInt()
}
