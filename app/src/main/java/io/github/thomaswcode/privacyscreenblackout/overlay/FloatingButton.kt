package io.github.thomaswcode.privacyscreenblackout.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Small draggable button. Tap opens a mini menu; press and hold peeks under the mask until released.
 */
class FloatingButton(
    private val context: Context,
    private val wm: WindowManager,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onToggleShow()
        fun onEdit()
        fun onNextLayout()
        fun onLock()
        /** Return true if the peek started. */
        fun onPeek(start: Boolean): Boolean
        fun onMoved(x: Int, y: Int)
    }

    data class Model(val showing: Boolean, val locked: Boolean, val hasLayouts: Boolean)

    private val dp = context.resources.displayMetrics.density
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val bubble = Bubble(context)
    private val menu = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        background = GradientDrawable().apply {
            setColor(Color.argb(235, 0x20, 0x21, 0x24))
            cornerRadius = 12 * dp
        }
        val p = (4 * dp).toInt()
        setPadding(p, p, p, p)
    }
    private val params = OverlayWindows.params(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        touchable = true,
    )
    private var attached = false
    private var model = Model(showing = false, locked = false, hasLayouts = false)

    init {
        val size = (BUTTON_DP * dp).toInt()
        root.addView(bubble, LinearLayout.LayoutParams(size, size))
        root.addView(menu)
        bubble.setOnTouchListener(DragTouchListener())
        bubble.setOnClickListener { toggleMenu() }
    }

    fun show(x: Int, y: Int) {
        if (attached) return
        params.x = x
        params.y = y
        clampToScreen()
        try {
            wm.addView(root, params)
            attached = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not add floating button", e)
        }
    }

    fun hide() {
        if (!attached) return
        runCatching { wm.removeViewImmediate(root) }
        attached = false
        menu.visibility = View.GONE
    }

    /** Re-adds the window so it sits above windows added after it (e.g. touch blockers). */
    fun bringToFront() {
        if (!attached) return
        runCatching { wm.removeViewImmediate(root) }
        attached = false
        show(params.x, params.y)
    }

    fun setModel(m: Model) {
        if (m == model) return
        model = m
        bubble.invalidate()
        if (menu.visibility == View.VISIBLE) buildMenu()
    }

    private fun toggleMenu() {
        if (menu.visibility == View.VISIBLE) {
            menu.visibility = View.GONE
        } else {
            buildMenu()
            menu.visibility = View.VISIBLE
        }
        if (attached) runCatching { wm.updateViewLayout(root, params) }
    }

    private fun buildMenu() {
        menu.removeAllViews()
        if (model.locked) {
            menu.addView(item("🔒 Locked (unlock in app or notification)", enabled = false) {})
        } else {
            menu.addView(item(if (model.showing) "Hide" else "Show") { callbacks.onToggleShow() })
            menu.addView(item("Edit") { callbacks.onEdit() })
            menu.addView(item("Next layout", enabled = model.hasLayouts) { callbacks.onNextLayout() })
            menu.addView(item("Lock") { callbacks.onLock() })
        }
        menu.addView(item("Close menu") {})
    }

    private fun item(label: String, enabled: Boolean = true, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(if (enabled) Color.WHITE else Color.GRAY)
        val h = (12 * dp).toInt()
        val v = (10 * dp).toInt()
        setPadding(h, v, h, v)
        if (enabled) setOnClickListener {
            onClick()
            menu.visibility = View.GONE
            if (attached) runCatching { wm.updateViewLayout(root, params) }
        }
    }

    private fun clampToScreen() {
        val (w, h) = OverlayWindows.screenSize(context)
        val size = (BUTTON_DP * dp).toInt()
        params.x = params.x.coerceIn(0, (w - size).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (h - size).coerceAtLeast(0))
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val handler = Handler(Looper.getMainLooper())
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var peeking = false
        private val longPress = Runnable {
            if (!dragging) peeking = callbacks.onPeek(true)
            if (peeking) bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX; downRawY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; peeking = false
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!peeking && !dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampToScreen()
                        if (attached) runCatching { wm.updateViewLayout(root, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    when {
                        peeking -> callbacks.onPeek(false)
                        dragging -> callbacks.onMoved(params.x, params.y)
                        e.actionMasked == MotionEvent.ACTION_UP -> v.performClick()
                    }
                    dragging = false; peeking = false
                }
            }
            return true
        }
    }

    /** The round button itself: a screen outline, filled when the mask is showing, with a dot when locked. */
    private inner class Bubble(context: Context) : View(context) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 0x20, 0x21, 0x24) }
        private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2 * dp }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val lock = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xC1, 0x07) }

        init {
            contentDescription = "Privacy Screen Blackout controls"
        }

        override fun performClick(): Boolean = super.performClick()

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            canvas.drawCircle(r, r, r, bg)
            val w = r * 0.55f
            val h = r * 0.8f
            if (model.showing) canvas.drawRoundRect(r - w / 2, r - h / 2, r + w / 2, r + h / 2, 3 * dp, 3 * dp, fill)
            canvas.drawRoundRect(r - w / 2, r - h / 2, r + w / 2, r + h / 2, 3 * dp, 3 * dp, fg)
            if (model.locked) canvas.drawCircle(r * 1.55f, r * 0.45f, r * 0.2f, lock)
        }
    }

    private companion object {
        const val TAG = "FloatingButton"
        const val BUTTON_DP = 48f
        const val LONG_PRESS_MS = 350L
    }
}
