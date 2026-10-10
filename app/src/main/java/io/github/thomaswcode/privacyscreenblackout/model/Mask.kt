package io.github.thomaswcode.privacyscreenblackout.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What a shape does to the mask: KEEP clears (content visible), BLACK fills. */
@Serializable
enum class Op { KEEP, BLACK }

/** Starting state of the mask before any shapes are applied. */
@Serializable
enum class Background { CLEAR, BLACK }

@Serializable
enum class Orientation { PORTRAIT, LANDSCAPE;
    companion object {
        fun of(width: Int, height: Int): Orientation = if (width > height) LANDSCAPE else PORTRAIT
    }
}

@Serializable
data class Pt(val x: Float, val y: Float)

/** Axis-aligned bounds used for hit testing and touch-cell classification. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** Inclusive of touching edges, so boundary pixels are treated as possibly covered (conservative). */
    fun intersects(l: Float, t: Float, r: Float, b: Float): Boolean =
        left <= r && right >= l && top <= b && bottom >= t
}

@Serializable
sealed class Shape {
    abstract val op: Op
    abstract fun bounds(): Bounds
    /** True when the point is inside the painted area of the shape. */
    abstract fun contains(x: Float, y: Float): Boolean
    /** True only when the whole rectangle is guaranteed to be inside the shape (may be conservative). */
    abstract fun containsRect(l: Float, t: Float, r: Float, b: Float): Boolean
    abstract fun translated(dx: Float, dy: Float): Shape
    abstract fun scaled(sx: Float, sy: Float): Shape
    abstract fun withOp(op: Op): Shape
}

@Serializable
@SerialName("rect")
data class RectShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    override val op: Op,
) : Shape() {
    override fun bounds() = Bounds(left, top, right, bottom)
    override fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    override fun containsRect(l: Float, t: Float, r: Float, b: Float) =
        l >= left && r <= right && t >= top && b <= bottom
    override fun translated(dx: Float, dy: Float) = copy(left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy)
    override fun scaled(sx: Float, sy: Float) = copy(left = left * sx, top = top * sy, right = right * sx, bottom = bottom * sy)
    override fun withOp(op: Op) = copy(op = op)

    companion object {
        /** Builds a rectangle from two corner points in any order. */
        fun fromCorners(a: Pt, b: Pt, op: Op) =
            RectShape(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y), op)
    }
}

/** Closed freehand polygon, filled using the non-zero winding rule (matches android.graphics.Path default). */
@Serializable
@SerialName("lasso")
data class LassoShape(val points: List<Pt>, override val op: Op) : Shape() {
    override fun bounds() = boundsOf(points, 0f)
    override fun contains(x: Float, y: Float) = points.size >= 3 && windingNumber(points, x, y) != 0
    override fun containsRect(l: Float, t: Float, r: Float, b: Float): Boolean {
        if (points.size < 3) return false
        if (!contains(l, t) || !contains(r, t) || !contains(l, b) || !contains(r, b)) return false
        // With all corners inside and no edge crossing the rectangle, the winding number is constant over it.
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            if (segmentIntersectsRect(p, q, l, t, r, b)) return false
        }
        return true
    }
    override fun translated(dx: Float, dy: Float) = copy(points = points.map { Pt(it.x + dx, it.y + dy) })
    override fun scaled(sx: Float, sy: Float) = copy(points = points.map { Pt(it.x * sx, it.y * sy) })
    override fun withOp(op: Op) = copy(op = op)
}

/** Freehand brush stroke: a polyline with round caps and joins. */
@Serializable
@SerialName("stroke")
data class StrokeShape(val points: List<Pt>, val width: Float, override val op: Op) : Shape() {
    override fun bounds() = boundsOf(points, width / 2f)
    override fun contains(x: Float, y: Float): Boolean {
        val r = width / 2f
        if (points.size == 1) return dist2(points[0].x, points[0].y, x, y) <= r * r
        for (i in 0 until points.size - 1) {
            if (distToSegment2(x, y, points[i], points[i + 1]) <= r * r) return true
        }
        return false
    }
    override fun containsRect(l: Float, t: Float, r: Float, b: Float): Boolean {
        // Conservative: the rectangle must fit inside a single capsule (each capsule is convex).
        val rad2 = (width / 2f) * (width / 2f)
        val segments = if (points.size == 1) listOf(points[0] to points[0]) else points.zipWithNext()
        return segments.any { (p, q) ->
            distToSegment2(l, t, p, q) <= rad2 && distToSegment2(r, t, p, q) <= rad2 &&
                distToSegment2(l, b, p, q) <= rad2 && distToSegment2(r, b, p, q) <= rad2
        }
    }
    override fun translated(dx: Float, dy: Float) = copy(points = points.map { Pt(it.x + dx, it.y + dy) })
    override fun scaled(sx: Float, sy: Float) =
        copy(points = points.map { Pt(it.x * sx, it.y * sy) }, width = width * (sx + sy) / 2f)
    override fun withOp(op: Op) = copy(op = op)
}

/** Coverage of a cell after all shapes are applied. */
enum class CellState { CLEAR_ALL, BLACK_ALL, MIXED }

/** One orientation of a layout: the screen size it was drawn at, the background and ordered shapes. */
@Serializable
data class MaskSpec(
    val width: Int,
    val height: Int,
    val background: Background = Background.CLEAR,
    val shapes: List<Shape> = emptyList(),
) {
    /** True when nothing would be drawn. */
    val isBlank: Boolean
        get() = background == Background.CLEAR && shapes.none { it.op == Op.BLACK }

    /** Point query; later shapes win over earlier ones. */
    fun isBlackAt(x: Float, y: Float): Boolean {
        var black = background == Background.BLACK
        for (s in shapes) if (s.contains(x, y)) black = s.op == Op.BLACK
        return black
    }

    /** Conservative cell query used to place touch blockers: BLACK_ALL is only returned when certain. */
    fun cellState(l: Float, t: Float, r: Float, b: Float): CellState {
        var state = if (background == Background.BLACK) CellState.BLACK_ALL else CellState.CLEAR_ALL
        for (s in shapes) {
            if (!s.bounds().intersects(l, t, r, b)) continue
            state = when {
                s.containsRect(l, t, r, b) -> if (s.op == Op.BLACK) CellState.BLACK_ALL else CellState.CLEAR_ALL
                s.op == Op.BLACK -> if (state == CellState.BLACK_ALL) CellState.BLACK_ALL else CellState.MIXED
                else -> if (state == CellState.CLEAR_ALL) CellState.CLEAR_ALL else CellState.MIXED
            }
        }
        return state
    }

    /** Swaps the background and every shape's operation. */
    fun inverted() = copy(
        background = if (background == Background.BLACK) Background.CLEAR else Background.BLACK,
        shapes = shapes.map { it.withOp(if (it.op == Op.BLACK) Op.KEEP else Op.BLACK) },
    )

    /** Rescales coordinates when the screen size differs from the one the mask was drawn at. */
    fun scaledTo(w: Int, h: Int): MaskSpec {
        if (w == width && h == height || width <= 0 || height <= 0) return this
        val sx = w.toFloat() / width
        val sy = h.toFloat() / height
        return copy(width = w, height = h, shapes = shapes.map { it.scaled(sx, sy) })
    }

    /** Adds a shape; the first KEEP shape on an empty mask turns the background black. */
    fun withShape(shape: Shape): MaskSpec {
        val bg = if (shapes.isEmpty() && background == Background.CLEAR && shape.op == Op.KEEP) Background.BLACK else background
        return copy(background = bg, shapes = shapes + shape)
    }

    /** Index of the top-most shape under the point, or -1. */
    fun hitTest(x: Float, y: Float, slop: Float): Int {
        for (i in shapes.indices.reversed()) {
            val s = shapes[i]
            if (s.contains(x, y)) return i
            if (slop > 0f && s is StrokeShape && s.copy(width = s.width + slop * 2).contains(x, y)) return i
            if (slop > 0f && s is RectShape && s.copy(left = s.left - slop, top = s.top - slop, right = s.right + slop, bottom = s.bottom + slop).contains(x, y)) return i
        }
        return -1
    }
}

@Serializable
data class Layout(
    val id: String,
    val name: String,
    val portrait: MaskSpec? = null,
    val landscape: MaskSpec? = null,
    /** 0.4–1.0; null uses the global default. */
    val opacity: Float? = null,
    /** ARGB colour; null uses the global default. */
    val color: Int? = null,
) {
    fun spec(o: Orientation): MaskSpec? = if (o == Orientation.PORTRAIT) portrait else landscape
    fun withSpec(o: Orientation, spec: MaskSpec?) =
        if (o == Orientation.PORTRAIT) copy(portrait = spec) else copy(landscape = spec)
}

// ---- geometry helpers ----

private fun boundsOf(points: List<Pt>, pad: Float): Bounds {
    if (points.isEmpty()) return Bounds(0f, 0f, 0f, 0f)
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    for (p in points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
    return Bounds(l - pad, t - pad, r + pad, b + pad)
}

private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx; val dy = ay - by
    return dx * dx + dy * dy
}

internal fun distToSegment2(x: Float, y: Float, p: Pt, q: Pt): Float {
    val vx = q.x - p.x; val vy = q.y - p.y
    val len2 = vx * vx + vy * vy
    if (len2 == 0f) return dist2(x, y, p.x, p.y)
    val tt = (((x - p.x) * vx + (y - p.y) * vy) / len2).coerceIn(0f, 1f)
    return dist2(x, y, p.x + tt * vx, p.y + tt * vy)
}

/** Non-zero winding number of the closed polygon around (x, y). */
internal fun windingNumber(poly: List<Pt>, x: Float, y: Float): Int {
    var wn = 0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val cross = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
        if (a.y <= y) {
            if (b.y > y && cross > 0) wn++
        } else {
            if (b.y <= y && cross < 0) wn--
        }
    }
    return wn
}

private fun orient(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float =
    (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

private fun segmentsIntersect(p1: Pt, p2: Pt, x3: Float, y3: Float, x4: Float, y4: Float): Boolean {
    val d1 = orient(x3, y3, x4, y4, p1.x, p1.y)
    val d2 = orient(x3, y3, x4, y4, p2.x, p2.y)
    val d3 = orient(p1.x, p1.y, p2.x, p2.y, x3, y3)
    val d4 = orient(p1.x, p1.y, p2.x, p2.y, x4, y4)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
    fun onSeg(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
        min(ax, bx) <= cx && cx <= max(ax, bx) && min(ay, by) <= cy && cy <= max(ay, by)
    if (abs(d1) == 0f && onSeg(x3, y3, x4, y4, p1.x, p1.y)) return true
    if (abs(d2) == 0f && onSeg(x3, y3, x4, y4, p2.x, p2.y)) return true
    if (abs(d3) == 0f && onSeg(p1.x, p1.y, p2.x, p2.y, x3, y3)) return true
    if (abs(d4) == 0f && onSeg(p1.x, p1.y, p2.x, p2.y, x4, y4)) return true
    return false
}

internal fun segmentIntersectsRect(p: Pt, q: Pt, l: Float, t: Float, r: Float, b: Float): Boolean {
    val inside = { pt: Pt -> pt.x > l && pt.x < r && pt.y > t && pt.y < b }
    if (inside(p) || inside(q)) return true
    return segmentsIntersect(p, q, l, t, r, t) || segmentsIntersect(p, q, r, t, r, b) ||
        segmentsIntersect(p, q, r, b, l, b) || segmentsIntersect(p, q, l, b, l, t)
}
