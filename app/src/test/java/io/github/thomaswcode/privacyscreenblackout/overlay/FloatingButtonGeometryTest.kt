package io.github.thomaswcode.privacyscreenblackout.overlay

import io.github.thomaswcode.privacyscreenblackout.overlay.FloatingButtonGeometry.clampY
import io.github.thomaswcode.privacyscreenblackout.overlay.FloatingButtonGeometry.placeMenu
import io.github.thomaswcode.privacyscreenblackout.overlay.FloatingButtonGeometry.snapX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingButtonGeometryTest {

    private fun place(x: Int, y: Int) = placeMenu(
        buttonX = x, buttonY = y, buttonSize = 100,
        menuWidth = 400, menuHeight = 600,
        screenWidth = 1080, screenHeight = 2340,
    )

    @Test
    fun snapsToNearerEdge() {
        assertEquals(0, snapX(buttonX = 300, buttonSize = 100, screenWidth = 1080))
        assertEquals(980, snapX(buttonX = 600, buttonSize = 100, screenWidth = 1080))
        // Already at an edge: stays there.
        assertEquals(0, snapX(buttonX = 0, buttonSize = 100, screenWidth = 1080))
        assertEquals(980, snapX(buttonX = 980, buttonSize = 100, screenWidth = 1080))
    }

    @Test
    fun clampsYOnScreen() {
        assertEquals(0, clampY(buttonY = -50, buttonSize = 100, screenHeight = 2340))
        assertEquals(2240, clampY(buttonY = 5000, buttonSize = 100, screenHeight = 2340))
    }

    @Test
    fun leftEdgeTopOpensRightAndDown() {
        val p = place(0, 200)
        assertFalse(p.alignEnd)
        assertFalse(p.menuAbove)
        assertEquals(0, p.x)
        assertEquals(200, p.y)
    }

    @Test
    fun rightEdgeOpensLeftKeepingButtonInPlace() {
        val p = place(980, 200)
        assertTrue(p.alignEnd)
        // The 400-wide window ends at the screen edge, so the right-aligned button stays at x = 980.
        assertEquals(680, p.x)
    }

    @Test
    fun bottomOpensUpKeepingButtonInPlace() {
        val p = place(0, 2000)
        assertTrue(p.menuAbove)
        // The 600-high menu sits above the button, so the button stays at y = 2000.
        assertEquals(1400, p.y)
    }

    @Test
    fun windowIsClampedOnScreen() {
        val p = placeMenu(
            buttonX = 1000, buttonY = 0, buttonSize = 100,
            menuWidth = 2000, menuHeight = 3000,
            screenWidth = 1080, screenHeight = 2340,
        )
        assertEquals(0, p.x)
        assertEquals(0, p.y)
    }
}
