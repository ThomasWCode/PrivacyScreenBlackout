package io.github.thomaswcode.privacyscreenblackout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class Settings(
    val loaded: Boolean = false,
    val activeLayoutId: String? = null,
    val wasShowing: Boolean = false,
    val locked: Boolean = false,
    val floatingButton: Boolean = true,
    val floatingX: Int = 24,
    val floatingY: Int = 400,
    val volumePeek: Boolean = true,
    val restoreVolume: Boolean = true,
    val autoOffMinutes: Int = 0,
    val defaultOpacity: Float = 1f,
    val defaultColor: Int = DEFAULT_COLOR,
    val brushWidthDp: Float = 32f,
    /** package name -> layout id */
    val appRules: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF000000.toInt()
        val AUTO_OFF_CHOICES = listOf(0, 5, 15, 30, 60)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, private val scope: CoroutineScope) {

    private val store = context.applicationContext.dataStore

    val settings: StateFlow<Settings> = store.data.map { p ->
        Settings(
            loaded = true,
            activeLayoutId = p[ACTIVE_LAYOUT],
            wasShowing = p[WAS_SHOWING] ?: false,
            locked = p[LOCKED] ?: false,
            floatingButton = p[FLOATING_BUTTON] ?: true,
            floatingX = p[FLOATING_X] ?: 24,
            floatingY = p[FLOATING_Y] ?: 400,
            volumePeek = p[VOLUME_PEEK] ?: true,
            restoreVolume = p[RESTORE_VOLUME] ?: true,
            autoOffMinutes = p[AUTO_OFF] ?: 0,
            defaultOpacity = p[DEFAULT_OPACITY] ?: 1f,
            defaultColor = p[DEFAULT_COLOR] ?: Settings.DEFAULT_COLOR,
            brushWidthDp = p[BRUSH_WIDTH] ?: 32f,
            appRules = decodeRules(p[APP_RULES]),
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    fun setActiveLayout(id: String?) = edit { if (id == null) it.remove(ACTIVE_LAYOUT) else it[ACTIVE_LAYOUT] = id }
    fun setWasShowing(v: Boolean) = edit { it[WAS_SHOWING] = v }
    fun setLocked(v: Boolean) = edit { it[LOCKED] = v }
    fun setFloatingButton(v: Boolean) = edit { it[FLOATING_BUTTON] = v }
    fun setFloatingPosition(x: Int, y: Int) = edit { it[FLOATING_X] = x; it[FLOATING_Y] = y }
    fun setVolumePeek(v: Boolean) = edit { it[VOLUME_PEEK] = v }
    fun setRestoreVolume(v: Boolean) = edit { it[RESTORE_VOLUME] = v }
    fun setAutoOffMinutes(v: Int) = edit { it[AUTO_OFF] = v }
    fun setDefaultOpacity(v: Float) = edit { it[DEFAULT_OPACITY] = v }
    fun setDefaultColor(v: Int) = edit { it[DEFAULT_COLOR] = v }
    fun setBrushWidthDp(v: Float) = edit { it[BRUSH_WIDTH] = v }

    fun setAppRule(pkg: String, layoutId: String?) = edit { p ->
        val rules = decodeRules(p[APP_RULES]).toMutableMap()
        if (layoutId == null) rules.remove(pkg) else rules[pkg] = layoutId
        p[APP_RULES] = json.encodeToString(rules)
    }

    /** Removes rules that point at a deleted layout. */
    fun removeRulesFor(layoutId: String) = edit { p ->
        p[APP_RULES] = json.encodeToString(decodeRules(p[APP_RULES]).filterValues { it != layoutId })
    }

    private fun decodeRules(raw: String?): Map<String, String> =
        raw?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { store.edit(block) }
    }

    private companion object {
        val json = Json
        val ACTIVE_LAYOUT = stringPreferencesKey("active_layout")
        val WAS_SHOWING = booleanPreferencesKey("was_showing")
        val LOCKED = booleanPreferencesKey("locked")
        val FLOATING_BUTTON = booleanPreferencesKey("floating_button")
        val FLOATING_X = intPreferencesKey("floating_x")
        val FLOATING_Y = intPreferencesKey("floating_y")
        val VOLUME_PEEK = booleanPreferencesKey("volume_peek")
        val RESTORE_VOLUME = booleanPreferencesKey("restore_volume")
        val AUTO_OFF = intPreferencesKey("auto_off_minutes")
        val DEFAULT_OPACITY = floatPreferencesKey("default_opacity")
        val DEFAULT_COLOR = intPreferencesKey("default_color")
        val BRUSH_WIDTH = floatPreferencesKey("brush_width_dp")
        val APP_RULES = stringPreferencesKey("app_rules")
    }
}
