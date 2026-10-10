package io.github.thomaswcode.privacyscreenblackout.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP

/** Window parameter helpers for accessibility overlays that cover the whole display (bars and cutout included). */
object OverlayWindows {

    fun params(
        width: Int,
        height: Int,
        touchable: Boolean,
        x: Int = 0,
        y: Int = 0,
        gravity: Int = Gravity.TOP or Gravity.START,
    ): LP {
        var flags = LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or LP.FLAG_NOT_TOUCHABLE
        return LP(width, height, LP.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun fullScreen(context: Context, touchable: Boolean): LP {
        val (w, h) = screenSize(context)
        return params(w, h, touchable)
    }

    /** Full display size in the current rotation, in pixels. */
    fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            p.x to p.y
        }
    }

    fun dp(context: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
}
