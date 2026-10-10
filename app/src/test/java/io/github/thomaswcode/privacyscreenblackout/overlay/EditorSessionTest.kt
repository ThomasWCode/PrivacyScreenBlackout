package io.github.thomaswcode.privacyscreenblackout.overlay

import io.github.thomaswcode.privacyscreenblackout.model.Background
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Op
import io.github.thomaswcode.privacyscreenblackout.model.RectShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {

    private val rect = RectShape(10f, 10f, 50f, 50f, Op.KEEP)

    @Test
    fun undoRedo() {
        val s = EditorSession(MaskSpec(100, 100))
        s.add(rect)
        assertEquals(1, s.spec.shapes.size)
        assertTrue(s.canUndo)
        s.undo()
        assertEquals(0, s.spec.shapes.size)
        assertEquals(Background.CLEAR, s.spec.background)
        s.redo()
        assertEquals(1, s.spec.shapes.size)
        assertFalse(s.canRedo)
    }

    @Test
    fun dragIsOneUndoStep() {
        val s = EditorSession(MaskSpec(100, 100))
        s.add(rect)
        s.select(0)
        s.beginGesture()
        s.updateSelectedLive(rect.translated(5f, 0f))
        s.updateSelectedLive(rect.translated(10f, 0f))
        s.endGesture()
        assertEquals(rect.translated(10f, 0f), s.spec.shapes.single())
        s.undo()
        assertEquals(rect, s.spec.shapes.single())
    }

    @Test
    fun setOpChangesSelectedShape() {
        val s = EditorSession(MaskSpec(100, 100))
        s.add(rect)
        s.select(0)
        s.setOp(Op.BLACK)
        assertEquals(Op.BLACK, s.spec.shapes.single().op)
        assertEquals(Op.BLACK, s.op)
    }

    @Test
    fun deleteSelected() {
        val s = EditorSession(MaskSpec(100, 100))
        s.add(rect)
        s.select(0)
        s.deleteSelected()
        assertTrue(s.spec.shapes.isEmpty())
        assertEquals(-1, s.selected)
    }

    @Test
    fun resizeRectKeepsOppositeCornerAndNormalises() {
        val r = RectShape(10f, 10f, 50f, 50f, Op.BLACK)
        assertEquals(RectShape(10f, 10f, 80f, 90f, Op.BLACK), EditorSession.resizeRect(r, 2, 80f, 90f))
        // Dragging the top-left corner past the bottom-right flips cleanly.
        assertEquals(RectShape(50f, 50f, 60f, 70f, Op.BLACK), EditorSession.resizeRect(r, 0, 60f, 70f))
    }
}
