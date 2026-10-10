package io.github.thomaswcode.privacyscreenblackout.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import io.github.thomaswcode.privacyscreenblackout.model.Op

/**
 * The editor: a full-screen drawing window plus a toolbar window that can sit at the top or bottom.
 */
class EditorOverlay(
    private val context: Context,
    private val wm: WindowManager,
    initial: MaskSpec,
    brushWidthDp: Float,
    private val onBrushWidthDp: (Float) -> Unit,
    private val onDone: (MaskSpec) -> Unit,
    private val onSaveNew: (MaskSpec) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val dp = context.resources.displayMetrics.density
    private val session = EditorSession(initial)
    private var brushDp = brushWidthDp
    private val editorView = EditorView(context, session) { brushDp * dp }
    private val toolbar = LinearLayout(context)
    private var toolbarAtTop = false
    private var shown = false

    private val toolChips = mutableMapOf<Tool, TextView>()
    private lateinit var keepChip: TextView
    private lateinit var blackChip: TextView
    private lateinit var undoChip: TextView
    private lateinit var redoChip: TextView
    private lateinit var deleteChip: TextView
    private lateinit var brushRow: View
    private lateinit var hint: TextView

    init {
        buildToolbar()
        session.onChanged = {
            editorView.invalidate()
            refresh()
        }
        refresh()
    }

    fun show() {
        if (shown) return
        try {
            wm.addView(editorView, OverlayWindows.fullScreen(context, touchable = true))
            wm.addView(toolbar, toolbarParams())
            shown = true
        } catch (e: Exception) {
            Log.e(TAG, "Could not show editor", e)
            dismiss()
        }
    }

    fun dismiss() {
        runCatching { wm.removeViewImmediate(toolbar) }
        runCatching { wm.removeViewImmediate(editorView) }
        shown = false
    }

    private fun toolbarParams() = OverlayWindows.params(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        touchable = true,
        gravity = (if (toolbarAtTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL,
    ).apply {
        // Keep the toolbar clear of the status/navigation bars and cutout.
        flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        }
    }

    private fun buildToolbar() {
        toolbar.orientation = LinearLayout.VERTICAL
        toolbar.background = GradientDrawable().apply {
            setColor(Color.argb(235, 0x20, 0x21, 0x24))
            cornerRadius = 16 * dp
        }
        val pad = (8 * dp).toInt()
        toolbar.setPadding(pad, pad, pad, pad)

        hint = TextView(context).apply {
            setTextColor(Color.rgb(0xE0, 0xE0, 0xE0))
            textSize = 13f
            setPadding(pad / 2, 0, pad / 2, pad / 2)
        }
        toolbar.addView(hint)

        val tools = row()
        Tool.entries.forEach { t ->
            val label = when (t) {
                Tool.RECT -> "▭ Rect"
                Tool.LASSO -> "➰ Lasso"
                Tool.BRUSH -> "🖌 Brush"
                Tool.SELECT -> "✥ Select"
            }
            toolChips[t] = chip(label) { session.tool = t; refresh() }.also { tools.addView(it) }
        }
        tools.addView(divider())
        keepChip = chip("Keep") { session.setOp(Op.KEEP) }.also { tools.addView(it) }
        blackChip = chip("Black") { session.setOp(Op.BLACK) }.also { tools.addView(it) }
        tools.addView(divider())
        undoChip = chip("↶ Undo") { session.undo() }.also { tools.addView(it) }
        redoChip = chip("↷ Redo") { session.redo() }.also { tools.addView(it) }
        deleteChip = chip("🗑 Delete") { session.deleteSelected() }.also { tools.addView(it) }
        tools.addView(chip("⇄ Invert") { session.invert() })
        tools.addView(chip("Clear") { session.clear() })
        toolbar.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
        })

        val brush = row()
        brush.addView(TextView(context).apply {
            text = "Width"
            setTextColor(Color.WHITE)
            setPadding(pad, 0, pad, 0)
        })
        brush.addView(SeekBar(context).apply {
            max = (MAX_BRUSH_DP - MIN_BRUSH_DP).toInt()
            progress = (brushDp - MIN_BRUSH_DP).toInt().coerceIn(0, max)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, value: Int, fromUser: Boolean) {
                    brushDp = MIN_BRUSH_DP + value
                }
                override fun onStartTrackingTouch(s: SeekBar) = Unit
                override fun onStopTrackingTouch(s: SeekBar) = onBrushWidthDp(brushDp)
            })
        })
        brushRow = brush
        toolbar.addView(brush)

        val actions = row()
        actions.addView(chip("⇅ Move bar") {
            toolbarAtTop = !toolbarAtTop
            if (shown) runCatching { wm.updateViewLayout(toolbar, toolbarParams()) }
        })
        actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(chip("Cancel") { onCancel() })
        actions.addView(chip("Save as new") { onSaveNew(session.spec) })
        actions.addView(chip("✓ Done", accent = true) { onDone(session.spec) })
        toolbar.addView(actions)
    }

    private fun refresh() {
        toolChips.forEach { (t, v) -> setActive(v, t == session.tool) }
        setActive(keepChip, session.op == Op.KEEP)
        setActive(blackChip, session.op == Op.BLACK)
        setEnabled(undoChip, session.canUndo)
        setEnabled(redoChip, session.canRedo)
        setEnabled(deleteChip, session.selected >= 0)
        brushRow.visibility = if (session.tool == Tool.BRUSH) View.VISIBLE else View.GONE
        hint.text = when {
            session.tool == Tool.SELECT -> "Tap a shape to select. Drag to move; drag a corner dot to resize."
            session.op == Op.KEEP -> "KEEP: draw areas to stay visible. Everything else is blacked out."
            else -> "BLACK: draw areas to black out."
        }
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(Color.argb(80, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), (28 * dp).toInt()).apply {
            setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        }
    }

    private fun chip(label: String, accent: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        val h = (12 * dp).toInt()
        val v = (8 * dp).toInt()
        setPadding(h, v, h, v)
        tag = accent
        background = chipBackground(active = false, accent = accent)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
        }
        setOnClickListener { onClick() }
    }

    private fun chipBackground(active: Boolean, accent: Boolean) = GradientDrawable().apply {
        cornerRadius = 18 * dp
        setColor(
            when {
                accent -> Color.rgb(0x1E, 0x88, 0xE5)
                active -> Color.rgb(0x5C, 0x6B, 0xC0)
                else -> Color.argb(60, 255, 255, 255)
            },
        )
    }

    private fun setActive(v: TextView, active: Boolean) {
        v.background = chipBackground(active, v.tag == true)
    }

    private fun setEnabled(v: TextView, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.4f
    }

    private companion object {
        const val TAG = "EditorOverlay"
        const val MIN_BRUSH_DP = 8f
        const val MAX_BRUSH_DP = 120f
    }
}
