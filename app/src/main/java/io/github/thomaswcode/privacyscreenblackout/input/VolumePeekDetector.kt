package io.github.thomaswcode.privacyscreenblackout.input

/**
 * Detects "press volume-down, release, press again and hold" to peek under the mask.
 *
 * The first press is always passed through (volume drops one step as usual). A second press
 * within [doublePressWindowMs] of the first release is consumed and starts a peek; its key
 * repeats and its release are also consumed, and the release ends the peek.
 */
class VolumePeekDetector(
    private val doublePressWindowMs: Long = 350,
    private val maxFirstPressMs: Long = 400,
) {
    enum class Action { NONE, START_PEEK, END_PEEK }

    data class Result(val consume: Boolean, val action: Action = Action.NONE)

    private enum class State { IDLE, FIRST_DOWN, WAIT_SECOND, PEEKING, SWALLOW_UNTIL_UP }

    private var state = State.IDLE
    private var firstDownAt = 0L
    private var firstUpAt = 0L

    val isPeeking get() = state == State.PEEKING

    /**
     * @param isDown true for ACTION_DOWN, false for ACTION_UP
     * @param repeatCount KeyEvent.getRepeatCount()
     * @param eventTime KeyEvent.getEventTime() in ms
     * @param enabled whether peeking is currently allowed (mask showing, not locked, setting on)
     */
    fun onVolumeDown(isDown: Boolean, repeatCount: Int, eventTime: Long, enabled: Boolean): Result {
        if (!enabled && state != State.PEEKING && state != State.SWALLOW_UNTIL_UP) {
            state = State.IDLE
            return PASS
        }
        return if (isDown) onDown(repeatCount, eventTime) else onUp(eventTime)
    }

    private fun onDown(repeatCount: Int, now: Long): Result {
        if (repeatCount > 0) {
            return when (state) {
                State.PEEKING, State.SWALLOW_UNTIL_UP -> CONSUME
                else -> PASS
            }
        }
        return when {
            state == State.PEEKING || state == State.SWALLOW_UNTIL_UP -> CONSUME
            state == State.WAIT_SECOND && now - firstUpAt <= doublePressWindowMs -> {
                state = State.PEEKING
                Result(true, Action.START_PEEK)
            }
            else -> {
                state = State.FIRST_DOWN
                firstDownAt = now
                PASS
            }
        }
    }

    private fun onUp(now: Long): Result = when (state) {
        State.PEEKING -> {
            state = State.IDLE
            Result(true, Action.END_PEEK)
        }
        State.SWALLOW_UNTIL_UP -> {
            state = State.IDLE
            CONSUME
        }
        State.FIRST_DOWN -> {
            // A long first press is a normal volume hold, not the start of a double press.
            if (now - firstDownAt <= maxFirstPressMs) {
                state = State.WAIT_SECOND
                firstUpAt = now
            } else state = State.IDLE
            PASS
        }
        else -> {
            state = State.IDLE
            PASS
        }
    }

    /** Ends a peek early (safety timeout or mask hidden). Remaining repeats and the release are still consumed. */
    fun forceEnd(): Boolean {
        if (state != State.PEEKING) return false
        state = State.SWALLOW_UNTIL_UP
        return true
    }

    fun reset() {
        state = State.IDLE
    }

    private companion object {
        val PASS = Result(false)
        val CONSUME = Result(true)
    }
}
