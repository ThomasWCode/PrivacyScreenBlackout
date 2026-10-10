package io.github.thomaswcode.privacyscreenblackout.input

import io.github.thomaswcode.privacyscreenblackout.input.VolumePeekDetector.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePeekDetectorTest {

    private val d = VolumePeekDetector(doublePressWindowMs = 350, maxFirstPressMs = 400)

    private fun down(t: Long, repeat: Int = 0, enabled: Boolean = true) = d.onVolumeDown(true, repeat, t, enabled)
    private fun up(t: Long, enabled: Boolean = true) = d.onVolumeDown(false, 0, t, enabled)

    @Test
    fun singlePressPassesThrough() {
        assertFalse(down(0).consume)
        assertFalse(up(100).consume)
        assertFalse(d.isPeeking)
    }

    @Test
    fun fastDoublePressAndHoldPeeksUntilRelease() {
        assertFalse(down(0).consume)
        assertFalse(up(80).consume)
        val second = down(300)
        assertTrue(second.consume)
        assertEquals(Action.START_PEEK, second.action)
        assertTrue(d.isPeeking)
        // Key repeats while holding are swallowed.
        assertTrue(down(800, repeat = 1).consume)
        assertTrue(down(850, repeat = 2).consume)
        val release = up(2000)
        assertTrue(release.consume)
        assertEquals(Action.END_PEEK, release.action)
        assertFalse(d.isPeeking)
    }

    @Test
    fun slowDoublePressIsTwoNormalPresses() {
        down(0); up(80)
        val second = down(80 + 351)
        assertFalse(second.consume)
        assertEquals(Action.NONE, second.action)
        assertFalse(up(500).consume)
    }

    @Test
    fun longFirstPressDoesNotArmDoublePress() {
        down(0)
        assertFalse(down(500, repeat = 1).consume)
        up(900)
        assertFalse(down(1000).consume)
    }

    @Test
    fun disabledPassesEverything() {
        assertFalse(down(0, enabled = false).consume)
        assertFalse(up(50, enabled = false).consume)
        assertFalse(down(100, enabled = false).consume)
        assertFalse(d.isPeeking)
    }

    @Test
    fun forceEndSwallowsRemainingRepeatsAndRelease() {
        down(0); up(50); down(200)
        assertTrue(d.forceEnd())
        assertFalse(d.isPeeking)
        assertTrue(down(15_300, repeat = 40).consume)
        val release = up(16_000)
        assertTrue(release.consume)
        assertEquals(Action.NONE, release.action)
        // Back to normal afterwards.
        assertFalse(down(17_000).consume)
    }

    @Test
    fun peekEndsOnReleaseEvenIfDisabledMidway() {
        down(0); up(50); down(200)
        val release = up(1000, enabled = false)
        assertTrue(release.consume)
        assertEquals(Action.END_PEEK, release.action)
    }
}
