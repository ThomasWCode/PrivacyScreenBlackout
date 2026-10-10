package io.github.thomaswcode.privacyscreenblackout.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.thomaswcode.privacyscreenblackout.App
import io.github.thomaswcode.privacyscreenblackout.data.Settings
import io.github.thomaswcode.privacyscreenblackout.input.VolumePeekDetector
import io.github.thomaswcode.privacyscreenblackout.model.Layout
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Orientation
import io.github.thomaswcode.privacyscreenblackout.notify.BlackoutNotifier
import io.github.thomaswcode.privacyscreenblackout.overlay.EditorOverlay
import io.github.thomaswcode.privacyscreenblackout.overlay.FloatingButton
import io.github.thomaswcode.privacyscreenblackout.overlay.MaskView
import io.github.thomaswcode.privacyscreenblackout.overlay.OverlayWindows
import io.github.thomaswcode.privacyscreenblackout.overlay.TouchBlockerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts every overlay window as a trusted accessibility overlay, so touches pass through visible areas.
 * Also watches the foreground app (per-app layouts) and volume-down (peek). Never reads screen content.
 */
class BlackoutAccessibilityService : AccessibilityService() {

    private val app get() = application as App
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var themed: ContextThemeWrapper

    private var maskView: MaskView? = null
    private lateinit var blockers: TouchBlockerManager
    private var editor: EditorOverlay? = null
    private var editorOrientation: Orientation? = null
    private var floating: FloatingButton? = null
    private lateinit var notifier: BlackoutNotifier

    private val screen = MutableStateFlow(0 to 0)
    private val volumeDetector = VolumePeekDetector()
    private var wasVisible = false
    private var autoOffMinutesScheduled = -1

    private val autoOff = Runnable { BlackoutController.hide(force = true) }
    private val peekTimeout = Runnable {
        if (volumeDetector.forceEnd()) BlackoutController.setPeeking(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WindowManager::class.java)
        themed = ContextThemeWrapper(this, android.R.style.Theme_Material)
        blockers = TouchBlockerManager(this, wm)
        notifier = BlackoutNotifier(this)
        updateScreen()

        maskView = MaskView(this).also {
            try {
                wm.addView(it, OverlayWindows.fullScreen(this, touchable = false))
            } catch (e: Exception) {
                Log.e(TAG, "Could not add mask window", e)
            }
        }

        scope.launch {
            val settings = app.settings.settings.first { it.loaded }
            BlackoutController.onServiceConnected(restoreShowing = settings.wasShowing)
            combine(BlackoutController.state, app.layouts.layouts, app.settings.settings, screen) { s, l, st, size ->
                RenderInput(s, l, st, size)
            }.collect(::render)
        }
    }

    private data class RenderInput(
        val state: OverlayState,
        val layouts: List<Layout>,
        val settings: Settings,
        val screen: Pair<Int, Int>,
    )

    private fun render(input: RenderInput) {
        val (state, layouts, settings, size) = input
        val (w, h) = size
        val orientation = Orientation.of(w, h)
        val layout = layouts.firstOrNull { it.id == settings.activeLayoutId } ?: layouts.firstOrNull()
        val spec = layout?.spec(orientation)?.scaledTo(w, h)

        // Editor
        if (state.editing && editor == null) {
            startEditor(layout, orientation, w, h, settings)
        } else if (!state.editing && editor != null) {
            editor?.dismiss()
            editor = null
            editorOrientation = null
        }

        // Mask + touch blockers
        val visible = state.showing && !state.editing && spec != null && !spec.isBlank
        maskView?.setModel(
            if (visible) spec else null,
            layout?.color ?: settings.defaultColor,
            layout?.opacity ?: settings.defaultOpacity,
        )
        maskView?.alpha = if (state.peeking) 0f else 1f
        val blockersChanged = blockers.update(if (visible) spec else null)

        if (!state.peeking && volumeDetector.isPeeking) {
            handler.removeCallbacks(peekTimeout)
            volumeDetector.forceEnd()
        }

        // Floating button
        val fb = floating
        if (settings.floatingButton && !state.editing) {
            val button = fb ?: FloatingButton(themed, wm, floatingCallbacks).also { floating = it }
            button.setModel(FloatingButton.Model(state.showing, settings.locked, layouts.isNotEmpty()))
            button.setAppearance(settings.floatingSizeDp, settings.floatingOpacity)
            if (fb == null) button.show(settings.floatingX, settings.floatingY)
            else if (blockersChanged) button.bringToFront()
        } else if (fb != null) {
            fb.hide()
            floating = null
        }

        // Auto-off timer: (re)start when the mask becomes visible or the setting changes.
        val showingNow = state.showing && !state.editing
        if (showingNow && (!wasVisible || autoOffMinutesScheduled != settings.autoOffMinutes)) {
            handler.removeCallbacks(autoOff)
            if (settings.autoOffMinutes > 0) handler.postDelayed(autoOff, settings.autoOffMinutes * 60_000L)
            autoOffMinutesScheduled = settings.autoOffMinutes
        } else if (!showingNow) {
            handler.removeCallbacks(autoOff)
            autoOffMinutesScheduled = -1
        }
        wasVisible = showingNow

        notifier.update(
            BlackoutNotifier.Model(
                showing = state.showing,
                locked = settings.locked,
                layoutName = layout?.name,
                orientation = orientation,
                hasSpecForOrientation = spec != null && !spec.isBlank,
                layoutCount = layouts.size,
            ),
        )
    }

    private fun startEditor(layout: Layout?, orientation: Orientation, w: Int, h: Int, settings: Settings) {
        val initial = layout?.spec(orientation)?.scaledTo(w, h) ?: MaskSpec(w, h)
        editorOrientation = orientation
        editor = EditorOverlay(
            context = themed,
            wm = wm,
            initial = initial,
            brushWidthDp = settings.brushWidthDp,
            onBrushWidthDp = app.settings::setBrushWidthDp,
            onDone = { BlackoutController.finishEdit(orientation, it) },
            onSaveNew = { BlackoutController.saveAsNew(orientation, it) },
            onCancel = { BlackoutController.cancelEdit() },
        ).also { it.show() }
    }

    private val floatingCallbacks = object : FloatingButton.Callbacks {
        override fun onToggleShow() {
            BlackoutController.toggle()
        }
        override fun onEdit() = BlackoutController.startEdit()
        override fun onNextLayout() = BlackoutController.nextLayout()
        override fun onLock() = BlackoutController.setLocked(true)
        override fun onPeek(start: Boolean): Boolean = BlackoutController.setPeeking(start)
        override fun onMoved(x: Int, y: Int) = app.settings.setFloatingPosition(x, y)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val before = Orientation.of(screen.value.first, screen.value.second)
        updateScreen()
        val after = Orientation.of(screen.value.first, screen.value.second)
        if (editor != null && before != after) {
            Toast.makeText(this, "Editing cancelled: screen rotated", Toast.LENGTH_SHORT).show()
            BlackoutController.cancelEdit()
        }
        // Window sizes are fixed in pixels, so resize the full-screen mask window.
        maskView?.let { v -> runCatching { wm.updateViewLayout(v, OverlayWindows.fullScreen(this, touchable = false)) } }
        floating?.onScreenChanged()
    }

    private fun updateScreen() {
        val size = OverlayWindows.screenSize(this)
        screen.value = size
        BlackoutController.setOrientation(Orientation.of(size.first, size.second))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PACKAGES || pkg == currentImePackage()) return
        BlackoutController.onForegroundApp(pkg)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val st = BlackoutController.state.value
        val settings = app.settings.settings.value
        val enabled = settings.volumePeek && st.showing && !st.editing && !settings.locked
        val result = volumeDetector.onVolumeDown(
            isDown = event.action == KeyEvent.ACTION_DOWN,
            repeatCount = event.repeatCount,
            eventTime = event.eventTime,
            enabled = enabled,
        )
        when (result.action) {
            VolumePeekDetector.Action.START_PEEK -> {
                if (BlackoutController.setPeeking(true)) {
                    if (settings.restoreVolume) restoreVolumeStep()
                    handler.postDelayed(peekTimeout, MAX_PEEK_MS)
                } else volumeDetector.forceEnd()
            }
            VolumePeekDetector.Action.END_PEEK -> {
                handler.removeCallbacks(peekTimeout)
                BlackoutController.setPeeking(false)
            }
            VolumePeekDetector.Action.NONE -> Unit
        }
        return result.consume
    }

    /** Undoes the one volume step taken by the first press of the double press. */
    private fun restoreVolumeStep() {
        val audio = getSystemService(AudioManager::class.java)
        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
    }

    private fun currentImePackage(): String? =
        AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        editor?.dismiss()
        editor = null
        floating?.hide()
        floating = null
        if (::blockers.isInitialized) blockers.release()
        maskView?.let { runCatching { wm.removeViewImmediate(it) } }
        maskView = null
        if (::notifier.isInitialized) notifier.cancel()
        BlackoutController.onServiceDisconnected()
    }

    private companion object {
        const val TAG = "BlackoutService"
        const val MAX_PEEK_MS = 15_000L
        val IGNORED_PACKAGES = setOf("com.android.systemui", "android")
    }
}
