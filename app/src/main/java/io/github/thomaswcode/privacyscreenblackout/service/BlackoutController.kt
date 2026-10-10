package io.github.thomaswcode.privacyscreenblackout.service

import android.content.ComponentName
import android.service.quicksettings.TileService
import io.github.thomaswcode.privacyscreenblackout.App
import io.github.thomaswcode.privacyscreenblackout.model.Layout
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Orientation
import io.github.thomaswcode.privacyscreenblackout.tile.BlackoutTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runtime state shared by the service, tile, notification, floating button and app UI. */
data class OverlayState(
    val serviceConnected: Boolean = false,
    /** The user (or an app rule) wants the mask visible. */
    val showing: Boolean = false,
    val editing: Boolean = false,
    val peeking: Boolean = false,
    /** Package that auto-showed the mask, so it can be hidden again when that app is left. */
    val autoShownFor: String? = null,
    /** Active layout before an app rule switched it; restored when leaving rule apps. */
    val layoutBeforeRule: String? = null,
    val orientation: Orientation = Orientation.PORTRAIT,
)

/** Single source of truth for overlay state. All methods must be called on the main thread. */
object BlackoutController {

    private lateinit var app: App
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private val settings get() = app.settings.settings.value
    val locked: Boolean get() = settings.locked

    fun init(app: App) {
        this.app = app
        // Keep the Quick Settings tile in sync.
        app.scope.launch {
            state.map { it.showing to it.serviceConnected }.distinctUntilChanged().collect {
                TileService.requestListeningState(app, ComponentName(app, BlackoutTileService::class.java))
            }
        }
    }

    fun activeLayout(): Layout? = app.layouts.get(settings.activeLayoutId) ?: app.layouts.layouts.value.firstOrNull()

    fun onServiceConnected(restoreShowing: Boolean) {
        _state.update { OverlayState(serviceConnected = true, showing = restoreShowing, orientation = it.orientation) }
    }

    fun onServiceDisconnected() {
        _state.update { OverlayState(orientation = it.orientation) }
    }

    fun setOrientation(o: Orientation) = _state.update { it.copy(orientation = o) }

    /** Shows the mask. With no layouts yet, opens the editor instead. */
    fun show() {
        if (!_state.value.serviceConnected) return
        if (activeLayout() == null) {
            startEdit()
            return
        }
        _state.update { it.copy(showing = true, autoShownFor = null) }
        persistShowing()
    }

    /** Hides the mask unless locked. Returns false when the lock prevented it. */
    fun hide(force: Boolean = false): Boolean {
        if (locked && !force) return false
        _state.update { it.copy(showing = false, peeking = false, autoShownFor = null) }
        persistShowing()
        return true
    }

    fun toggle() = if (_state.value.showing) hide() else { show(); true }

    fun startEdit() {
        if (!_state.value.serviceConnected || locked) return
        _state.update { it.copy(editing = true, peeking = false) }
    }

    /** Saves the edited mask into the active layout (creating one if needed) and shows it. */
    fun finishEdit(orientation: Orientation, spec: MaskSpec) {
        val layout = activeLayout() ?: app.layouts.create()
        app.layouts.upsert(layout.withSpec(orientation, spec.takeUnless { it.isBlank && it.shapes.isEmpty() }))
        app.settings.setActiveLayout(layout.id)
        endEditAndShow()
    }

    /** Saves the edited mask as a brand-new layout and makes it active. */
    fun saveAsNew(orientation: Orientation, spec: MaskSpec) {
        val layout = app.layouts.create().withSpec(orientation, spec)
        app.layouts.upsert(layout)
        app.settings.setActiveLayout(layout.id)
        endEditAndShow()
    }

    fun cancelEdit() = _state.update { it.copy(editing = false) }

    private fun endEditAndShow() {
        _state.update { it.copy(editing = false, showing = true) }
        persistShowing()
    }

    fun setActiveLayout(id: String) {
        _state.update { it.copy(layoutBeforeRule = null) }
        app.settings.setActiveLayout(id)
    }

    fun nextLayout() {
        val list = app.layouts.layouts.value
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == activeLayout()?.id }
        setActiveLayout(list[(i + 1) % list.size].id)
    }

    /** Starts or stops a peek. Starting is refused while locked or when the mask is not showing. */
    fun setPeeking(peek: Boolean): Boolean {
        if (peek && (locked || !_state.value.showing || _state.value.editing)) return false
        _state.update { it.copy(peeking = peek) }
        return true
    }

    fun setLocked(lock: Boolean) {
        app.settings.setLocked(lock)
        if (lock) _state.update { it.copy(peeking = false) }
    }

    /** Applies per-app rules when the foreground app changes. */
    fun onForegroundApp(pkg: String) {
        val s = _state.value
        if (!s.serviceConnected || s.editing) return
        val ruleLayout = settings.appRules[pkg]?.takeIf { app.layouts.get(it) != null }
        if (ruleLayout != null) {
            val before = s.layoutBeforeRule ?: activeLayout()?.id
            app.settings.setActiveLayout(ruleLayout)
            _state.update {
                when {
                    !it.showing -> it.copy(showing = true, autoShownFor = pkg, layoutBeforeRule = before)
                    it.autoShownFor != null -> it.copy(autoShownFor = pkg, layoutBeforeRule = before)
                    else -> it.copy(layoutBeforeRule = before)
                }
            }
            return
        }
        s.layoutBeforeRule?.let { prev -> if (app.layouts.get(prev) != null) app.settings.setActiveLayout(prev) }
        _state.update {
            if (it.autoShownFor != null) it.copy(showing = false, peeking = false, autoShownFor = null, layoutBeforeRule = null)
            else it.copy(layoutBeforeRule = null)
        }
    }

    private fun persistShowing() {
        val s = _state.value
        app.settings.setWasShowing(s.showing && s.autoShownFor == null)
    }
}
