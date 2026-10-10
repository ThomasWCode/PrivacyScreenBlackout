package io.github.thomaswcode.privacyscreenblackout.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thomaswcode.privacyscreenblackout.App
import io.github.thomaswcode.privacyscreenblackout.data.Settings
import io.github.thomaswcode.privacyscreenblackout.model.Layout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class AppEntry(val pkg: String, val label: String)

@Composable
fun AppRulesSection(layouts: List<Layout>, settings: Settings) {
    val app = App.instance
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val labels by produceState(emptyMap<String, String>(), settings.appRules.keys) {
        value = withContext(Dispatchers.IO) { settings.appRules.keys.associateWith { appLabel(context, it) } }
    }

    Section("Per-app layouts") {
        Text(
            "When one of these apps opens, its layout is shown automatically and hidden again when you leave it.",
            style = MaterialTheme.typography.bodySmall,
        )
        settings.appRules.forEach { (pkg, layoutId) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(labels[pkg] ?: pkg, modifier = Modifier.weight(1f))
                LayoutDropdown(layouts, layoutId) { app.settings.setAppRule(pkg, it) }
                TextButton(onClick = { app.settings.setAppRule(pkg, null) }) { Text("Remove") }
            }
        }
        OutlinedButton(enabled = layouts.isNotEmpty(), onClick = { picking = true }) { Text("Add app") }
        if (layouts.isEmpty()) Text("Create a layout first.", style = MaterialTheme.typography.bodySmall)
    }

    if (picking) {
        AppPickerDialog(
            exclude = settings.appRules.keys,
            onDismiss = { picking = false },
            onPick = { pkg ->
                val layoutId = settings.activeLayoutId ?: layouts.first().id
                app.settings.setAppRule(pkg, layoutId)
                picking = false
            },
        )
    }
}

@Composable
private fun LayoutDropdown(layouts: List<Layout>, selectedId: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text((layouts.firstOrNull { it.id == selectedId }?.name ?: "?") + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            layouts.forEach { l ->
                DropdownMenuItem(text = { Text(l.name) }, onClick = { onPick(l.id); open = false })
            }
        }
    }
}

@Composable
private fun AppPickerDialog(exclude: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search") })
                val list = apps
                if (list == null) Text("Loading…")
                else LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(list.filter { it.pkg !in exclude && it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { entry ->
                        Text(
                            entry.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.pkg) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsSection(settings: Settings) {
    val s = App.instance.settings
    Section("Settings") {
        SwitchRow("Floating button", "Tap for a menu; press and hold to peek; drag to move.", settings.floatingButton, s::setFloatingButton)
        if (settings.floatingButton) {
            CommitSlider("Button size", settings.floatingSizeDp, 32f..96f, { "${it.roundToInt()} dp" }, s::setFloatingSizeDp)
            OpacitySlider("Button opacity", settings.floatingOpacity, 0.2f..1f, s::setFloatingOpacity)
        }
        SwitchRow(
            "Volume-down peek",
            "Press volume-down, then press again and hold to peek; release to hide. The first press still lowers volume one step.",
            settings.volumePeek,
            s::setVolumePeek,
        )
        if (settings.volumePeek) {
            SwitchRow("Restore volume after peek", "Raises volume back by the one step the first press took.", settings.restoreVolume, s::setRestoreVolume)
        }
        Text("Auto-off", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Settings.AUTO_OFF_CHOICES.forEach { m ->
                FilterChip(
                    selected = settings.autoOffMinutes == m,
                    onClick = { s.setAutoOffMinutes(m) },
                    label = { Text(if (m == 0) "Off" else "${m}m") },
                )
            }
        }
        OpacitySlider("Default opacity", settings.defaultOpacity, onCommit = s::setDefaultOpacity)
        Text("Default colour", style = MaterialTheme.typography.bodyLarge)
        ColorPicker(settings.defaultColor, s::setDefaultColor)
    }
}

private fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun appLabel(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) {
    pkg
}
