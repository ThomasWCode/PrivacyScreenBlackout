package io.github.thomaswcode.privacyscreenblackout.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import io.github.thomaswcode.privacyscreenblackout.model.Background
import io.github.thomaswcode.privacyscreenblackout.model.LassoShape
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Op
import io.github.thomaswcode.privacyscreenblackout.model.RectShape
import io.github.thomaswcode.privacyscreenblackout.model.Shape
import io.github.thomaswcode.privacyscreenblackout.model.StrokeShape

/** Draws a mask spec. Used by the display overlay and the editor so both always look the same. */
object MaskRenderer {

    fun pathFor(shape: Shape): Path = Path().apply {
        fillType = Path.FillType.WINDING
        when (shape) {
            is RectShape -> addRect(shape.left, shape.top, shape.right, shape.bottom, Path.Direction.CW)
            is LassoShape -> {
                shape.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
            is StrokeShape -> shape.points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
    }

    /**
     * Draws [spec] in [color] with overall [alpha] (0–255). Keep shapes clear what is beneath them
     * inside an offscreen layer, so they never punch through to content drawn earlier on the canvas.
     */
    fun draw(canvas: Canvas, spec: MaskSpec, color: Int, alpha: Int, paths: List<Path>? = null) {
        val opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = opaque; style = Paint.Style.FILL }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val save = canvas.saveLayerAlpha(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), alpha)
        if (spec.background == Background.BLACK) canvas.drawColor(opaque)
        spec.shapes.forEachIndexed { i, shape ->
            val paint = if (shape.op == Op.BLACK) fill else clear
            if (shape is StrokeShape) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = shape.width
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                if (shape.points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(shape.points[0].x, shape.points[0].y, shape.width / 2f, paint)
                } else {
                    canvas.drawPath(paths?.getOrNull(i) ?: pathFor(shape), paint)
                }
                paint.style = Paint.Style.FILL
            } else {
                canvas.drawPath(paths?.getOrNull(i) ?: pathFor(shape), paint)
            }
        }
        canvas.restoreToCount(save)
    }
}
