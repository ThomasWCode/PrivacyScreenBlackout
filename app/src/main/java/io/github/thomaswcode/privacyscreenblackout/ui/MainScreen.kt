package io.github.thomaswcode.privacyscreenblackout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thomaswcode.privacyscreenblackout.App
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutController
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val app = App.instance
    val state by BlackoutController.state.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val layouts by app.layouts.layouts.collectAsStateWithLifecycle()
    // Bumped on every resume so permission/setting checks refresh after returning from system settings.
    val resumeTick = remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick.intValue++
        onPauseOrDispose { }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Privacy Screen Blackout") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SetupSection(state.serviceConnected, resumeTick.intValue) }
            item {
                Section("Mask") {
                    val activeName = layouts.firstOrNull { it.id == settings.activeLayoutId }?.name ?: layouts.firstOrNull()?.name
                    Text(
                        when {
                            !state.serviceConnected -> "Enable the accessibility service to use the mask."
                            state.showing -> "Showing · ${activeName ?: "no layout"}"
                            else -> "Hidden"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = state.serviceConnected && !(settings.locked && state.showing),
                            onClick = { BlackoutController.toggle() },
                        ) { Text(if (state.showing) "Hide" else "Show") }
                        OutlinedButton(
                            enabled = state.serviceConnected && !settings.locked,
                            onClick = { BlackoutController.startEdit() },
                        ) { Text("Edit on screen") }
                    }
                    SwitchRow(
                        "Lock",
                        "Blocks Hide, Edit and Peek from the floating button, tile and volume key. Unlock here or from the notification.",
                        settings.locked,
                    ) { BlackoutController.setLocked(it) }
                }
            }
            item { LayoutsSection(layouts, settings) }
            item { AppRulesSection(layouts, settings) }
            item { SettingsSection(settings) }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Opacity slider that only saves when the drag ends, to avoid a write per frame. */
@Composable
fun OpacitySlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Text("$label ${(local * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local) },
        valueRange = 0.4f..1f,
    )
}
