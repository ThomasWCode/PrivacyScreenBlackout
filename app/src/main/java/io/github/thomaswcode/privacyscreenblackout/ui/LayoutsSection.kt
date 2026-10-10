package io.github.thomaswcode.privacyscreenblackout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.thomaswcode.privacyscreenblackout.App
import io.github.thomaswcode.privacyscreenblackout.data.Settings
import io.github.thomaswcode.privacyscreenblackout.model.Layout
import io.github.thomaswcode.privacyscreenblackout.service.BlackoutController

/** Mask colour presets (ARGB). */
val COLOR_PRESETS = listOf(
    0xFF000000.toInt(), // black
    0xFF202124.toInt(), // dark grey
    0xFF1A237E.toInt(), // navy
    0xFF1B5E20.toInt(), // dark green
    0xFF3E2723.toInt(), // brown
    0xFF4A148C.toInt(), // purple
)

@Composable
fun LayoutsSection(layouts: List<Layout>, settings: Settings) {
    val app = App.instance
    val activeId = settings.activeLayoutId ?: layouts.firstOrNull()?.id
    var renaming by remember { mutableStateOf<Layout?>(null) }
    var deleting by remember { mutableStateOf<Layout?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    Section("Layouts") {
        if (layouts.isEmpty()) {
            Text("No layouts yet. Tap “Edit on screen” (or New layout) and draw.", style = MaterialTheme.typography.bodyMedium)
        }
        layouts.forEachIndexed { i, layout ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = layout.id == activeId, onClick = { BlackoutController.setActiveLayout(layout.id) })
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = if (expanded == layout.id) null else layout.id },
                ) {
                    Text(layout.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Portrait ${if (layout.portrait != null) "✓" else "—"} · Landscape ${if (layout.landscape != null) "✓" else "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { expanded = if (expanded == layout.id) null else layout.id }) {
                    Text(if (expanded == layout.id) "Less" else "More")
                }
            }
            if (expanded == layout.id) {
                Column(modifier = Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OpacitySlider(
                        if (layout.opacity == null) "Opacity (default)" else "Opacity",
                        layout.opacity ?: settings.defaultOpacity,
                    ) { app.layouts.upsert(layout.copy(opacity = it)) }
                    Text("Colour${if (layout.color == null) " (default)" else ""}")
                    ColorPicker(layout.color ?: settings.defaultColor) { app.layouts.upsert(layout.copy(color = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { app.layouts.upsert(layout.copy(opacity = null, color = null)) }) { Text("Use defaults") }
                        TextButton(onClick = { renaming = layout }) { Text("Rename") }
                        TextButton(onClick = { deleting = layout }) { Text("Delete") }
                    }
                    Text(
                        "To draw: select this layout, tap “Edit on screen”, and rotate the phone first to draw the landscape version.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        OutlinedButton(onClick = {
            val created = app.layouts.create()
            BlackoutController.setActiveLayout(created.id)
        }) { Text("New layout") }
    }

    renaming?.let { layout ->
        var name by remember(layout.id) { mutableStateOf(layout.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename layout") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    app.layouts.upsert(layout.copy(name = name.trim()))
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { layout ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${layout.name}”?") },
            text = { Text("Per-app rules using this layout are removed too.") },
            confirmButton = {
                TextButton(onClick = {
                    app.layouts.delete(layout.id)
                    app.settings.removeRulesFor(layout.id)
                    if (settings.activeLayoutId == layout.id) app.settings.setActiveLayout(null)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COLOR_PRESETS.forEach { c ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(c), CircleShape)
                    .border(
                        width = if (c == selected) 3.dp else 1.dp,
                        color = if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}
