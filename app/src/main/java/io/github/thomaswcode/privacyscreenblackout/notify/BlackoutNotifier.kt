package io.github.thomaswcode.privacyscreenblackout.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thomaswcode.privacyscreenblackout.MainActivity
import io.github.thomaswcode.privacyscreenblackout.R
import io.github.thomaswcode.privacyscreenblackout.model.Orientation

/** Ongoing, low-importance notification with quick actions. */
class BlackoutNotifier(private val context: Context) {

    data class Model(
        val showing: Boolean,
        val locked: Boolean,
        val layoutName: String?,
        val orientation: Orientation,
        val hasSpecForOrientation: Boolean,
        val layoutCount: Int,
    )

    private val nm = NotificationManagerCompat.from(context)
    private var last: Model? = null

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Blackout controls", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    fun update(m: Model) {
        if (m == last || !nm.areNotificationsEnabled()) return
        last = m
        val orientationName = if (m.orientation == Orientation.PORTRAIT) "portrait" else "landscape"
        val title = when {
            m.showing && !m.hasSpecForOrientation -> "No $orientationName mask in ${m.layoutName ?: "layout"}"
            m.showing -> "Mask on · ${m.layoutName ?: "layout"}"
            else -> "Mask off"
        }
        val text = if (m.locked) "Locked: hiding and editing are disabled" else "Tap to open settings"
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        if (m.locked) {
            b.addAction(0, "Unlock", action(ActionReceiver.UNLOCK))
            if (!m.showing) b.addAction(0, "Show", action(ActionReceiver.TOGGLE))
        } else {
            b.addAction(0, if (m.showing) "Hide" else "Show", action(ActionReceiver.TOGGLE))
            val editLabel = if (m.showing && !m.hasSpecForOrientation) "Draw for $orientationName" else "Edit"
            b.addAction(0, editLabel, action(ActionReceiver.EDIT))
        }
        if (m.layoutCount > 1) b.addAction(0, "Next layout", action(ActionReceiver.NEXT_LAYOUT))
        try {
            nm.notify(ID, b.build())
        } catch (_: SecurityException) {
            // Notification permission revoked between the check and the call.
        }
    }

    fun cancel() {
        last = null
        nm.cancel(ID)
    }

    private fun action(name: String): PendingIntent = PendingIntent.getBroadcast(
        context, name.hashCode(),
        Intent(context, ActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "blackout_controls"
        const val ID = 1
    }
}
