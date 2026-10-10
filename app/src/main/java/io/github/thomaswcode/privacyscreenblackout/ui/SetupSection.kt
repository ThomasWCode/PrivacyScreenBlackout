package io.github.thomaswcode.privacyscreenblackout.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.thomaswcode.privacyscreenblackout.R
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutAccessibilityService
import io.github.thomaswcode.privacyscreenblackout.tile.BlackoutTileService

@Composable
fun SetupSection(serviceConnected: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    val serviceEnabled = remember(resumeTick, serviceConnected) { isServiceEnabled(context) }
    val notificationsGranted = remember(resumeTick) { notificationsGranted(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Section("Setup") {
        Step(
            done = null,
            title = "1. Allow restricted settings",
            detail = "Android blocks Accessibility for sideloaded apps until you allow it: App info → ⋮ → Allow restricted settings. " +
                "If the option is missing, try step 2 once first.",
            action = "App info",
        ) { context.startActivity(appDetailsIntent(context)) }
        Step(
            done = serviceEnabled && serviceConnected,
            title = "2. Turn on the accessibility service",
            detail = if (serviceEnabled && !serviceConnected) "Enabled but not running yet. Toggle it off and on." else
                "Settings → Accessibility → Privacy Screen Blackout.",
            action = "Open",
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Step(
            done = notificationsGranted,
            title = "3. Allow notifications",
            detail = "For the Show/Hide/Edit controls notification.",
            action = "Allow",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else context.startActivity(appDetailsIntent(context))
        }
        Step(
            done = null,
            title = "4. Add the Quick Settings tile",
            detail = "Toggle the mask from the notification shade.",
            action = "Add",
        ) { requestAddTile(context) }
        Step(
            done = null,
            title = "Optional: unrestricted battery",
            detail = "Some phones stop accessibility services to save battery. App info → Battery → Unrestricted.",
            action = "App info",
        ) { context.startActivity(appDetailsIntent(context)) }
    }
}

@Composable
private fun Step(done: Boolean?, title: String, detail: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val mark = when (done) {
                true -> "✅ "
                false -> "⚠️ "
                null -> ""
            }
            Text(mark + title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (done != true) TextButton(onClick = onAction) { Text(action) }
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, BlackoutAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
}

private fun notificationsGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sbm = context.getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(context, BlackoutTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_notification),
            ContextCompat.getMainExecutor(context),
        ) { }
    } else {
        Toast.makeText(context, "Pull down Quick Settings, tap the pencil and drag in “Blackout”.", Toast.LENGTH_LONG).show()
    }
}
