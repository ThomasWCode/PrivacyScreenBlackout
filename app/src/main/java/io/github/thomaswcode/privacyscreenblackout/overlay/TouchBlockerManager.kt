package io.github.thomaswcode.privacyscreenblackout.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.thomaswcode.privacyscreenblackout.geometry.RegionDecomposer
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec

/**
 * Places invisible touchable windows over the fully-black parts of the mask so taps there are swallowed.
 * Visible areas have no window on top, so the app underneath receives those touches.
 */
class TouchBlockerManager(private val context: Context, private val wm: WindowManager) {

    private val windows = mutableListOf<View>()
    private var current: MaskSpec? = null

    /** Rebuilds the blockers for [spec]; null removes them. Returns true when windows changed. */
    fun update(spec: MaskSpec?): Boolean {
        if (spec == current) return false
        current = spec
        clear()
        if (spec == null) return true
        val cellPx = OverlayWindows.dp(context, CELL_DP).toInt().coerceAtLeast(8)
        val rects = RegionDecomposer.blockerRects(spec, cellPx, MAX_WINDOWS)
        for (r in rects) {
            val v = BlockerView(context)
            try {
                wm.addView(v, OverlayWindows.params(r.width, r.height, touchable = true, x = r.left, y = r.top))
                windows += v
            } catch (e: Exception) {
                Log.e(TAG, "Could not add blocker window", e)
            }
        }
        return true
    }

    fun clear() {
        windows.forEach { runCatching { wm.removeViewImmediate(it) } }
        windows.clear()
    }

    fun release() {
        clear()
        current = null
    }

    @SuppressLint("ViewConstructor")
    private class BlockerView(context: Context) : View(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }

    private companion object {
        const val TAG = "TouchBlockerManager"
        const val CELL_DP = 16f
        const val MAX_WINDOWS = 48
    }
}
