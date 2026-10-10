package io.github.thomaswcode.privacyscreenblackout.geometry

import io.github.thomaswcode.privacyscreenblackout.model.Background
import io.github.thomaswcode.privacyscreenblackout.model.LassoShape
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Op
import io.github.thomaswcode.privacyscreenblackout.model.Pt
import io.github.thomaswcode.privacyscreenblackout.model.RectShape
import io.github.thomaswcode.privacyscreenblackout.model.StrokeShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionDecomposerTest {

    @Test
    fun mergesRunsAndStacksIdenticalRows() {
        val grid = arrayOf(
            booleanArrayOf(true, true, false, true),
            booleanArrayOf(true, true, false, false),
            booleanArrayOf(false, false, false, false),
        )
        val rects = RegionDecomposer.mergeCells(grid).map { it.toList() }.toSet()
        assertEquals(setOf(listOf(0, 0, 2, 2), listOf(3, 0, 4, 1)), rects)
    }

    @Test
    fun fullBlackScreenIsOneRect() {
        val spec = MaskSpec(1000, 2000, Background.BLACK)
        assertEquals(listOf(PixelRect(0, 0, 1000, 2000)), RegionDecomposer.blockerRects(spec, 40, 48))
    }

    @Test
    fun emptyMaskHasNoBlockers() {
        assertTrue(RegionDecomposer.blockerRects(MaskSpec(1000, 2000), 40, 48).isEmpty())
    }

    @Test
    fun keepWindowIsNeverCovered() {
        val spec = MaskSpec(1000, 2000).withShape(RectShape(110f, 510f, 590f, 1290f, Op.KEEP))
        val rects = RegionDecomposer.blockerRects(spec, 40, 48)
        assertTrue(rects.isNotEmpty())
        assertNoBlockerOverVisible(spec, rects)
        // Far corners of the screen are black and blocked.
        assertTrue(rects.any { it.left <= 5 && it.top <= 5 && it.right > 5 && it.bottom > 5 })
    }

    @Test
    fun freehandShapesNeverBlockVisiblePixels() {
        val lasso = LassoShape(listOf(Pt(100f, 100f), Pt(700f, 150f), Pt(500f, 900f), Pt(150f, 700f)), Op.KEEP)
        val stroke = StrokeShape(listOf(Pt(200f, 1200f), Pt(800f, 1600f), Pt(300f, 1900f)), 120f, Op.KEEP)
        val black = StrokeShape(listOf(Pt(300f, 300f), Pt(400f, 400f)), 80f, Op.BLACK)
        val spec = MaskSpec(1000, 2000).withShape(lasso).withShape(stroke).withShape(black)
        assertNoBlockerOverVisible(spec, RegionDecomposer.blockerRects(spec, 40, 48))
    }

    @Test
    fun respectsWindowCap() {
        // Checkerboard of small keep holes forces many rectangles at fine resolution.
        var spec = MaskSpec(1000, 2000, Background.BLACK)
        for (x in 0 until 10) for (y in 0 until 20) {
            if ((x + y) % 2 == 0) spec = spec.withShape(RectShape(x * 100f + 30, y * 100f + 30, x * 100f + 70, y * 100f + 70, Op.KEEP))
        }
        val rects = RegionDecomposer.blockerRects(spec, 20, 48)
        assertTrue(rects.size <= 48)
        assertNoBlockerOverVisible(spec, rects)
    }

    private fun assertNoBlockerOverVisible(spec: MaskSpec, rects: List<PixelRect>) {
        for (r in rects) {
            var y = r.top.toFloat()
            while (y < r.bottom) {
                var x = r.left.toFloat()
                while (x < r.right) {
                    assertTrue("Blocker over visible pixel at ($x, $y)", spec.isBlackAt(x, y))
                    x += 5f
                }
                y += 5f
            }
            assertFalse(r.width <= 0 || r.height <= 0)
        }
    }
}
