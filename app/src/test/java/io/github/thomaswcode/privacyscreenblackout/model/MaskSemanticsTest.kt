package io.github.thomaswcode.privacyscreenblackout.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskSemanticsTest {

    @Test
    fun firstKeepShapeTurnsBackgroundBlack() {
        val spec = MaskSpec(100, 200).withShape(RectShape(10f, 10f, 50f, 50f, Op.KEEP))
        assertEquals(Background.BLACK, spec.background)
        assertFalse(spec.isBlackAt(20f, 20f))
        assertTrue(spec.isBlackAt(80f, 150f))
    }

    @Test
    fun firstBlackShapeKeepsBackgroundClear() {
        val spec = MaskSpec(100, 200).withShape(RectShape(10f, 10f, 50f, 50f, Op.BLACK))
        assertEquals(Background.CLEAR, spec.background)
        assertTrue(spec.isBlackAt(20f, 20f))
        assertFalse(spec.isBlackAt(80f, 150f))
    }

    @Test
    fun laterShapesWin() {
        val spec = MaskSpec(100, 200)
            .withShape(RectShape(0f, 0f, 100f, 100f, Op.BLACK))
            .withShape(RectShape(20f, 20f, 40f, 40f, Op.KEEP))
            .withShape(RectShape(25f, 25f, 30f, 30f, Op.BLACK))
        assertTrue(spec.isBlackAt(5f, 5f))
        assertFalse(spec.isBlackAt(22f, 22f))
        assertTrue(spec.isBlackAt(27f, 27f))
    }

    @Test
    fun invertSwapsEverything() {
        val spec = MaskSpec(100, 200).withShape(RectShape(10f, 10f, 50f, 50f, Op.KEEP))
        val inv = spec.inverted()
        assertEquals(Background.CLEAR, inv.background)
        assertEquals(Op.BLACK, inv.shapes.single().op)
        for (x in listOf(5f, 20f, 70f)) for (y in listOf(5f, 20f, 150f)) {
            assertEquals(!spec.isBlackAt(x, y), inv.isBlackAt(x, y))
        }
        assertEquals(spec, inv.inverted())
    }

    @Test
    fun blankDetection() {
        assertTrue(MaskSpec(10, 10).isBlank)
        assertTrue(MaskSpec(10, 10).withShape(RectShape(0f, 0f, 5f, 5f, Op.BLACK)).inverted().inverted().let { !it.isBlank })
        assertFalse(MaskSpec(10, 10, Background.BLACK).isBlank)
    }

    @Test
    fun cellStateIsConservative() {
        val spec = MaskSpec(100, 100).withShape(RectShape(10f, 10f, 50f, 50f, Op.KEEP))
        assertEquals(CellState.BLACK_ALL, spec.cellState(60f, 60f, 90f, 90f))
        assertEquals(CellState.CLEAR_ALL, spec.cellState(20f, 20f, 40f, 40f))
        assertEquals(CellState.MIXED, spec.cellState(40f, 40f, 60f, 60f))
    }

    @Test
    fun lassoUsesNonZeroWinding() {
        val square = LassoShape(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f)), Op.BLACK)
        assertTrue(square.contains(5f, 5f))
        assertFalse(square.contains(15f, 5f))
        assertTrue(square.containsRect(2f, 2f, 8f, 8f))
        assertFalse(square.containsRect(2f, 2f, 12f, 8f))
        // Concave "C" shape: the notch is outside even though all corners of its bounding box are inside.
        val c = LassoShape(listOf(Pt(0f, 0f), Pt(30f, 0f), Pt(30f, 10f), Pt(10f, 10f), Pt(10f, 20f), Pt(30f, 20f), Pt(30f, 30f), Pt(0f, 30f)), Op.BLACK)
        assertFalse(c.contains(20f, 15f))
        assertFalse(c.containsRect(5f, 5f, 25f, 25f))
    }

    @Test
    fun strokeContainment() {
        val s = StrokeShape(listOf(Pt(0f, 50f), Pt(100f, 50f)), 20f, Op.BLACK)
        assertTrue(s.contains(50f, 55f))
        assertFalse(s.contains(50f, 65f))
        assertTrue(s.containsRect(40f, 45f, 60f, 55f))
        assertFalse(s.containsRect(40f, 35f, 60f, 55f))
    }

    @Test
    fun scaledToResizesCoordinates() {
        val spec = MaskSpec(100, 200).withShape(RectShape(10f, 20f, 50f, 100f, Op.BLACK))
        val scaled = spec.scaledTo(200, 400)
        assertEquals(RectShape(20f, 40f, 100f, 200f, Op.BLACK), scaled.shapes.single())
        assertEquals(200, scaled.width)
    }

    @Test
    fun hitTestPicksTopMost() {
        val spec = MaskSpec(100, 100)
            .withShape(RectShape(0f, 0f, 50f, 50f, Op.BLACK))
            .withShape(RectShape(25f, 25f, 75f, 75f, Op.BLACK))
        assertEquals(1, spec.hitTest(30f, 30f, 0f))
        assertEquals(0, spec.hitTest(10f, 10f, 0f))
        assertEquals(-1, spec.hitTest(90f, 90f, 0f))
        assertEquals(1, spec.hitTest(78f, 78f, 5f))
    }
}
