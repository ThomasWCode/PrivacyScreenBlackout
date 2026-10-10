package io.github.thomaswcode.privacyscreenblackout.overlay

/** Pure position maths for [FloatingButton], kept separate so it can be unit tested. */
object FloatingButtonGeometry {

    /** The x of whichever side edge is nearer the button's centre. */
    fun snapX(buttonX: Int, buttonSize: Int, screenWidth: Int): Int {
        val right = (screenWidth - buttonSize).coerceAtLeast(0)
        return if (buttonX + buttonSize / 2 > screenWidth / 2) right else 0
    }

    /** Keeps the button fully on screen. */
    fun clampY(buttonY: Int, buttonSize: Int, screenHeight: Int): Int =
        buttonY.coerceIn(0, (screenHeight - buttonSize).coerceAtLeast(0))

    /**
     * Where the window goes while the menu is open. The menu opens towards the larger side of the
     * screen, so it is never squeezed against an edge, and the button keeps its position.
     */
    fun placeMenu(
        buttonX: Int,
        buttonY: Int,
        buttonSize: Int,
        menuWidth: Int,
        menuHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): MenuPlacement {
        val alignEnd = buttonX + buttonSize / 2 > screenWidth / 2
        val menuAbove = buttonY + buttonSize / 2 > screenHeight / 2
        val windowWidth = maxOf(buttonSize, menuWidth)
        val windowHeight = buttonSize + menuHeight
        val x = if (alignEnd) buttonX + buttonSize - windowWidth else buttonX
        val y = if (menuAbove) buttonY - menuHeight else buttonY
        return MenuPlacement(
            x = x.coerceIn(0, (screenWidth - windowWidth).coerceAtLeast(0)),
            y = y.coerceIn(0, (screenHeight - windowHeight).coerceAtLeast(0)),
            alignEnd = alignEnd,
            menuAbove = menuAbove,
        )
    }
}

data class MenuPlacement(
    /** Window position. */
    val x: Int,
    val y: Int,
    /** Button and menu are right-aligned, so the menu extends to the left of the button. */
    val alignEnd: Boolean,
    /** The menu sits above the button. */
    val menuAbove: Boolean,
)
