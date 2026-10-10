package io.github.thomaswcode.privacyscreenblackout.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.thomaswcode.privacyscreenblackout.MainActivity
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutController

/** Quick Settings tile: tap toggles the mask, or opens setup if the accessibility service is off. */
class BlackoutTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!BlackoutController.state.value.serviceConnected) {
            openApp()
            return
        }
        BlackoutController.toggle()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = BlackoutController.state.value
        tile.state = when {
            !s.serviceConnected -> Tile.STATE_INACTIVE
            s.showing -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !s.serviceConnected -> "Set up"
                BlackoutController.locked -> "Locked"
                s.showing -> "On"
                else -> "Off"
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
