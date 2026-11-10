package io.github.giannoccarol.corollalauncher.panels

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.databinding.DialogSheetBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemSheetRowBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemSheetSliderBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemSheetSwitchBinding

/**
 * The launcher's own panel, used instead of jumping to system settings: a rounded card over the
 * dimmed home screen, in the current theme, with helpers for the few kinds of rows panels need.
 * The system bars stay hidden while it is open.
 */
open class Sheet(protected val activity: Activity, @StringRes title: Int) {

    private val dialog = Dialog(activity, R.style.Theme_Launcher_Sheet)
    private val binding = DialogSheetBinding.inflate(activity.layoutInflater)
    protected val inflater = activity.layoutInflater

    /** Where rows go, top to bottom. */
    protected val content: LinearLayout get() = binding.content

    init {
        binding.title.setText(title)
        binding.close.setOnClickListener { dismiss() }
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { onDismissed() }
        dialog.window?.apply {
            val width = (activity.resources.displayMetrics.widthPixels * WIDTH_FRACTION).toInt()
            setLayout(width.coerceAtMost(maxWidthPx()), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.getInsetsController(this, decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    open fun show() {
        render()
        onShown()
        dialog.show()
    }

    fun dismiss() = dialog.dismiss()

    val isShowing: Boolean get() = dialog.isShowing

    /** Rebuilds the rows from the current state. */
    fun render() {
        content.removeAllViews()
        build()
    }

    /** Adds the rows. Called on show and on every [render]. */
    protected open fun build() = Unit

    /** Register receivers here; [onDismissed] releases them. */
    protected open fun onShown() = Unit

    protected open fun onDismissed() = Unit

    protected fun label(@StringRes text: Int) {
        val view = inflater.inflate(R.layout.item_sheet_label, content, false) as TextView
        view.setText(text)
        content.addView(view)
    }

    protected fun switchRow(
        @StringRes title: Int,
        summary: CharSequence?,
        checked: Boolean,
        onToggle: () -> Unit,
    ) {
        val row = ItemSheetSwitchBinding.inflate(inflater, content, false)
        row.title.setText(title)
        setSummary(row.summary, summary)
        row.toggle.isChecked = checked
        row.root.setOnClickListener { onToggle() }
        row.root.contentDescription = activity.getString(title)
        content.addView(row.root)
    }

    protected fun sliderRow(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        max: Int,
        value: Int,
        showValue: (Int) -> String,
        step: Int = 1,
        onChange: (Int) -> Unit,
    ): SeekBar {
        val row = ItemSheetSliderBinding.inflate(inflater, content, false)
        row.title.setText(title)
        row.icon.setImageResource(icon)
        row.slider.max = max
        row.slider.progress = value
        row.slider.contentDescription = activity.getString(title)
        row.value.text = showValue(value)
        row.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                row.value.text = showValue(progress)
                if (fromUser) onChange(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        // The buttons are the precise way to adjust while driving; the slider is the quick one.
        fun nudge(delta: Int) {
            val next = (row.slider.progress + delta).coerceIn(0, max)
            if (next == row.slider.progress) return
            row.slider.progress = next
            onChange(next)
        }
        row.minus.setOnClickListener { nudge(-step) }
        row.plus.setOnClickListener { nudge(step) }
        content.addView(row.root)
        return row.slider
    }

    protected fun row(
        title: CharSequence,
        summary: CharSequence? = null,
        @DrawableRes icon: Int? = null,
        @DrawableRes trailing: Int? = null,
        onClick: (() -> Unit)?,
    ): View {
        val row = ItemSheetRowBinding.inflate(inflater, content, false)
        row.title.text = title
        setSummary(row.summary, summary)
        icon?.let {
            row.icon.setImageResource(it)
            row.icon.visibility = View.VISIBLE
        }
        trailing?.let {
            row.trailing.setImageResource(it)
            row.trailing.visibility = View.VISIBLE
        }
        if (onClick != null) {
            row.root.setOnClickListener { onClick() }
        } else {
            row.root.isClickable = false
            row.root.isFocusable = false
        }
        content.addView(row.root)
        return row.root
    }

    protected fun button(@StringRes text: Int, onClick: () -> Unit) {
        val view = inflater.inflate(R.layout.item_sheet_button, content, false) as TextView
        view.setText(text)
        view.setOnClickListener { onClick() }
        content.addView(view)
    }

    private fun setSummary(view: TextView, summary: CharSequence?) {
        view.text = summary
        view.visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun maxWidthPx(): Int = (MAX_WIDTH_DP * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val WIDTH_FRACTION = 0.62f
        const val MAX_WIDTH_DP = 640
    }
}
