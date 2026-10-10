package io.github.thomaswcode.privacyscreenblackout.overlay

import io.github.thomaswcode.privacyscreenblackout.model.Background
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Op
import io.github.thomaswcode.privacyscreenblackout.model.Pt
import io.github.thomaswcode.privacyscreenblackout.model.RectShape
import io.github.thomaswcode.privacyscreenblackout.model.Shape

enum class Tool { RECT, LASSO, BRUSH, SELECT }

/** Editing state with undo/redo. Free of Android types so it can be unit-tested. */
class EditorSession(initial: MaskSpec) {

    var spec: MaskSpec = initial
        private set
    var tool: Tool = Tool.RECT
    var op: Op = if (initial.background == Background.BLACK) Op.KEEP else Op.BLACK
        private set
    /** Index of the selected shape, or -1. */
    var selected: Int = -1
        private set

    private val undoStack = ArrayDeque<MaskSpec>()
    private val redoStack = ArrayDeque<MaskSpec>()
    private var gestureStart: MaskSpec? = null

    var onChanged: () -> Unit = {}

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    val selectedShape: Shape? get() = spec.shapes.getOrNull(selected)

    private fun commit(next: MaskSpec) {
        if (next == spec) return
        undoStack.addLast(spec)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        spec = next
        if (selected >= spec.shapes.size) selected = -1
        onChanged()
    }

    fun add(shape: Shape) {
        selected = -1
        commit(spec.withShape(shape))
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(spec)
        spec = prev
        selected = -1
        onChanged()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(spec)
        spec = next
        selected = -1
        onChanged()
    }

    fun invert() {
        op = if (op == Op.KEEP) Op.BLACK else Op.KEEP
        commit(spec.inverted())
    }

    fun clear() {
        selected = -1
        commit(spec.copy(background = Background.CLEAR, shapes = emptyList()))
    }

    /** Sets the operation for new shapes; also changes the selected shape, if any. */
    fun setOp(newOp: Op) {
        op = newOp
        val sel = selectedShape
        if (sel != null && sel.op != newOp) commit(spec.copy(shapes = spec.shapes.replaced(selected, sel.withOp(newOp))))
        else onChanged()
    }

    fun select(index: Int) {
        selected = index
        selectedShape?.let { op = it.op }
        onChanged()
    }

    fun deleteSelected() {
        if (selected < 0) return
        val i = selected
        selected = -1
        commit(spec.copy(shapes = spec.shapes.filterIndexed { idx, _ -> idx != i }))
    }

    /** Call at the start of a move/resize drag so the whole drag becomes one undo step. */
    fun beginGesture() {
        gestureStart = spec
    }

    /** Replaces the selected shape during a drag without recording history. */
    fun updateSelectedLive(shape: Shape) {
        if (selected < 0) return
        spec = spec.copy(shapes = spec.shapes.replaced(selected, shape))
        onChanged()
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != spec) {
            undoStack.addLast(start)
            redoStack.clear()
        }
        onChanged()
    }

    companion object {
        private const val MAX_HISTORY = 100

        /** Moves corner [corner] (0 TL, 1 TR, 2 BR, 3 BL) of [orig] to (x, y), keeping the opposite corner fixed. */
        fun resizeRect(orig: RectShape, corner: Int, x: Float, y: Float): RectShape {
            val opposite = when (corner) {
                0 -> Pt(orig.right, orig.bottom)
                1 -> Pt(orig.left, orig.bottom)
                2 -> Pt(orig.left, orig.top)
                else -> Pt(orig.right, orig.top)
            }
            return RectShape.fromCorners(opposite, Pt(x, y), orig.op)
        }

        fun corners(r: RectShape) = listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))

        private fun <T> List<T>.replaced(i: Int, v: T) = mapIndexed { idx, old -> if (idx == i) v else old }
    }
}
