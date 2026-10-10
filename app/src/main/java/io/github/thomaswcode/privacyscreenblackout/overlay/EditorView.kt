package io.github.thomaswcode.privacyscreenblackout.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import io.github.thomaswcode.privacyscreenblackout.model.LassoShape
import io.github.thomaswcode.privacyscreenblackout.model.Op
import io.github.thomaswcode.privacyscreenblackout.model.Pt
import io.github.thomaswcode.privacyscreenblackout.model.RectShape
import io.github.thomaswcode.privacyscreenblackout.model.Shape
import io.github.thomaswcode.privacyscreenblackout.model.StrokeShape
import kotlin.math.abs
import kotlin.math.hypot

/** Full-screen drawing surface for the editor. Shows a semi-transparent preview so content stays visible. */
@SuppressLint("ViewConstructor")
class EditorView(
    context: Context,
    private val session: EditorSession,
    private val brushWidthPx: () -> Float,
) : View(context) {

    private val dp = context.resources.displayMetrics.density
    private val minRectPx = 12 * dp
    private val pointSpacingPx = 3 * dp
    private val handleRadius = 10 * dp
    private val handleTouch = 28 * dp
    private val selectSlop = 12 * dp

    private val outlineKeep = strokePaint(Color.rgb(0x4C, 0xAF, 0x50), 2 * dp)
    private val outlineBlack = strokePaint(Color.rgb(0xF4, 0x43, 0x36), 2 * dp)
    private val outlineSelected = strokePaint(Color.rgb(0xFF, 0xC1, 0x07), 3 * dp).apply {
        pathEffect = DashPathEffect(floatArrayOf(10 * dp, 6 * dp), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xC1, 0x07) }
    private val gesturePaint = strokePaint(Color.WHITE, 2 * dp)
    private val brushPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.argb(160, 255, 255, 255)
    }

    // In-progress gesture
    private var downPt: Pt? = null
    private var curPt: Pt? = null
    private val points = ArrayList<Pt>()
    private var dragOrig: Shape? = null
    private var resizeCorner = -1

    override fun onDraw(canvas: Canvas) {
        val spec = session.spec
        MaskRenderer.draw(canvas, spec, Color.BLACK, PREVIEW_ALPHA)
        spec.shapes.forEachIndexed { i, s ->
            val path = MaskRenderer.pathFor(s)
            // For strokes this outlines the centre line; the preview fill above shows the width.
            canvas.drawPath(path, if (s.op == Op.KEEP) outlineKeep else outlineBlack)
            if (i == session.selected) {
                canvas.drawPath(path, outlineSelected)
                if (s is RectShape) EditorSession.corners(s).forEach { c -> canvas.drawCircle(c.x, c.y, handleRadius, handlePaint) }
            }
        }
        drawGesture(canvas)
    }

    private fun drawGesture(canvas: Canvas) {
        val d = downPt ?: return
        val c = curPt ?: return
        when (session.tool) {
            Tool.RECT -> canvas.drawRect(minOf(d.x, c.x), minOf(d.y, c.y), maxOf(d.x, c.x), maxOf(d.y, c.y), gesturePaint)
            Tool.LASSO -> canvas.drawPath(polyPath(points, close = false), gesturePaint)
            Tool.BRUSH -> {
                brushPreview.strokeWidth = brushWidthPx()
                canvas.drawPath(polyPath(points, close = false), brushPreview)
            }
            Tool.SELECT -> Unit
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = Pt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(p)
            MotionEvent.ACTION_MOVE -> onMove(p)
            MotionEvent.ACTION_UP -> onUp(p)
            MotionEvent.ACTION_CANCEL -> reset()
        }
        invalidate()
        return true
    }

    private fun onDown(p: Pt) {
        downPt = p
        curPt = p
        points.clear()
        points += p
        if (session.tool == Tool.SELECT) {
            val sel = session.selectedShape
            resizeCorner = if (sel is RectShape) {
                EditorSession.corners(sel).indexOfFirst { hypot(it.x - p.x, it.y - p.y) <= handleTouch }
            } else -1
            if (resizeCorner < 0) {
                val hit = session.spec.hitTest(p.x, p.y, selectSlop)
                session.select(hit)
            }
            dragOrig = session.selectedShape
            if (dragOrig != null) session.beginGesture()
        }
    }

    private fun onMove(p: Pt) {
        curPt = p
        when (session.tool) {
            Tool.LASSO, Tool.BRUSH -> {
                val last = points.last()
                if (hypot(p.x - last.x, p.y - last.y) >= pointSpacingPx) points += p
            }
            Tool.SELECT -> {
                val orig = dragOrig ?: return
                val d = downPt ?: return
                val moved = if (resizeCorner >= 0 && orig is RectShape) {
                    EditorSession.resizeRect(orig, resizeCorner, p.x, p.y)
                } else orig.translated(p.x - d.x, p.y - d.y)
                session.updateSelectedLive(moved)
            }
            Tool.RECT -> Unit
        }
    }

    private fun onUp(p: Pt) {
        val d = downPt
        val op = session.op
        when (session.tool) {
            Tool.RECT -> if (d != null && abs(p.x - d.x) >= minRectPx && abs(p.y - d.y) >= minRectPx) {
                session.add(RectShape.fromCorners(d, p, op))
            }
            Tool.LASSO -> {
                if (points.last() != p) points += p
                if (points.size >= 3) session.add(LassoShape(points.toList(), op))
            }
            Tool.BRUSH -> {
                if (points.last() != p) points += p
                session.add(StrokeShape(points.toList(), brushWidthPx(), op))
            }
            Tool.SELECT -> if (dragOrig != null) session.endGesture()
        }
        reset()
    }

    private fun reset() {
        downPt = null
        curPt = null
        points.clear()
        dragOrig = null
        resizeCorner = -1
    }

    private fun polyPath(pts: List<Pt>, close: Boolean) = Path().apply {
        pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        if (close) close()
    }

    private fun strokePaint(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; color = c; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    private companion object {
        /** Preview opacity so the content underneath stays visible while drawing. */
        const val PREVIEW_ALPHA = 150
    }
}
