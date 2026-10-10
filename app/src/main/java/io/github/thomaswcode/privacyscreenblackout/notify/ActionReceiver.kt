package io.github.thomaswcode.privacyscreenblackout.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutController

/** Handles notification action buttons. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TOGGLE -> BlackoutController.toggle()
            EDIT -> BlackoutController.startEdit()
            NEXT_LAYOUT -> BlackoutController.nextLayout()
            UNLOCK -> BlackoutController.setLocked(false)
        }
    }

    companion object {
        const val TOGGLE = "io.github.thomaswcode.privacyscreenblackout.TOGGLE"
        const val EDIT = "io.github.thomaswcode.privacyscreenblackout.EDIT"
        const val NEXT_LAYOUT = "io.github.thomaswcode.privacyscreenblackout.NEXT_LAYOUT"
        const val UNLOCK = "io.github.thomaswcode.privacyscreenblackout.UNLOCK"
    }
}
