package io.github.thomaswcode.privacyscreenblackout.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Small draggable button that snaps to the nearer side edge. Tap opens a mini menu; press and hold
 * peeks under the mask until released.
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
    private var sizePx = (DEFAULT_SIZE_DP * dp).toInt()
    /** The round button's position. The window starts elsewhere while the menu opens to the left or above. */
    private var buttonX = 0
    private var buttonY = 0
    private var onRightEdge = false
    private var snapAnimator: ValueAnimator? = null

    init {
        root.addView(bubble, LinearLayout.LayoutParams(sizePx, sizePx))
        // Explicit WRAP_CONTENT: a vertical LinearLayout's default MATCH_PARENT child would be squeezed to the bubble's width.
        root.addView(menu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bubble.setOnTouchListener(DragTouchListener())
        bubble.setOnClickListener { toggleMenu() }
    }

    fun show(x: Int, y: Int) {
        if (attached) return
        buttonX = x
        buttonY = y
        snapNow()
        applyLayout()
        try {
            wm.addView(root, params)
            attached = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not add floating button", e)
        }
    }

    fun hide() {
        if (!attached) return
        snapAnimator?.cancel()
        runCatching { wm.removeViewImmediate(root) }
        attached = false
        menu.visibility = View.GONE
    }

    /** Re-adds the window so it sits above windows added after it (e.g. touch blockers). */
    fun bringToFront() {
        if (!attached) return
        runCatching { wm.removeViewImmediate(root) }
        attached = false
        show(buttonX, buttonY)
    }

    /** Keeps the button on the same side edge after the screen size changes, e.g. on rotation. */
    fun onScreenChanged() {
        if (!attached) return
        snapAnimator?.cancel()
        val (w, h) = OverlayWindows.screenSize(context)
        buttonX = if (onRightEdge) (w - sizePx).coerceAtLeast(0) else 0
        buttonY = FloatingButtonGeometry.clampY(buttonY, sizePx, h)
        relayout()
    }

    /** Applies the user's size (dp) and opacity (0–1) settings to the round button. */
    fun setAppearance(sizeDp: Float, opacity: Float) {
        val px = (sizeDp * dp).toInt()
        bubble.alpha = opacity.coerceIn(MIN_OPACITY, 1f)
        if (px == sizePx) return
        sizePx = px
        bubble.layoutParams = LinearLayout.LayoutParams(px, px)
        snapNow()
        relayout()
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
        relayout()
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
            relayout()
        }
    }

    /** Moves the button straight to the nearer side edge, e.g. for a saved position or a new size. */
    private fun snapNow() {
        snapAnimator?.cancel()
        val (w, h) = OverlayWindows.screenSize(context)
        buttonX = FloatingButtonGeometry.snapX(buttonX, sizePx, w)
        buttonY = FloatingButtonGeometry.clampY(buttonY, sizePx, h)
        onRightEdge = buttonX > 0
    }

    /** Slides the button to the nearer side edge after a drag and saves where it ends up. */
    private fun animateSnap() {
        val (w, _) = OverlayWindows.screenSize(context)
        val target = FloatingButtonGeometry.snapX(buttonX, sizePx, w)
        onRightEdge = target > 0
        callbacks.onMoved(target, buttonY)
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(buttonX, target).apply {
            duration = SNAP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                buttonX = it.animatedValue as Int
                relayout()
            }
            start()
        }
    }

    /** Sets the window position from the button position, opening the menu towards the roomier side. */
    private fun applyLayout() {
        if (menu.visibility != View.VISIBLE) {
            params.x = buttonX
            params.y = buttonY
            return
        }
        val (w, h) = OverlayWindows.screenSize(context)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        menu.measure(unspecified, unspecified)
        val p = FloatingButtonGeometry.placeMenu(buttonX, buttonY, sizePx, menu.measuredWidth, menu.measuredHeight, w, h)
        root.gravity = if (p.alignEnd) Gravity.END else Gravity.START
        if ((root.getChildAt(0) === menu) != p.menuAbove) {
            root.removeAllViews()
            if (p.menuAbove) {
                root.addView(menu)
                root.addView(bubble)
            } else {
                root.addView(bubble)
                root.addView(menu)
            }
        }
        params.x = p.x
        params.y = p.y
    }

    private fun relayout() {
        applyLayout()
        if (attached) runCatching { wm.updateViewLayout(root, params) }
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
                    snapAnimator?.cancel()
                    downRawX = e.rawX; downRawY = e.rawY
                    startX = buttonX; startY = buttonY
                    dragging = false; peeking = false
                    handler.postDelayed(longPress, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!peeking && !dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                        menu.visibility = View.GONE
                    }
                    if (dragging) {
                        val (w, h) = OverlayWindows.screenSize(context)
                        buttonX = (startX + dx.toInt()).coerceIn(0, (w - sizePx).coerceAtLeast(0))
                        buttonY = FloatingButtonGeometry.clampY(startY + dy.toInt(), sizePx, h)
                        relayout()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    when {
                        peeking -> callbacks.onPeek(false)
                        dragging -> animateSnap()
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
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x20, 0x21, 0x24) }
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
        const val DEFAULT_SIZE_DP = 48f
        const val MIN_OPACITY = 0.2f
        const val LONG_PRESS_MS = 350L
        const val SNAP_MS = 180L
    }
}
