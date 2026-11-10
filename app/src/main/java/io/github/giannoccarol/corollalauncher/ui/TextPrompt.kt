package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import io.github.giannoccarol.corollalauncher.R

/** A one-field dialog, used for destinations and saved addresses. */
object TextPrompt {

    fun show(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes hintRes: Int,
        initial: String?,
        @StringRes positiveRes: Int,
        onResult: (String) -> Unit,
    ) = show(context, context.getString(titleRes), hintRes, initial, positiveRes, password = false, onResult)

    /** [password] hides the typed text, for Wi-Fi passwords. */
    fun show(
        context: Context,
        title: CharSequence,
        @StringRes hintRes: Int,
        initial: String?,
        @StringRes positiveRes: Int,
        password: Boolean,
        onResult: (String) -> Unit,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_text_prompt, null)
        val input = view.findViewById<EditText>(R.id.input)
        if (password) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        input.setHint(hintRes)
        input.setText(initial.orEmpty())
        input.setSelection(input.text.length)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(positiveRes) { _, _ -> onResult(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        // Done on the on-screen keyboard, or Enter on a physical or remote keyboard.
        input.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                true
            } else {
                false
            }
        }
        dialog.show()
        input.requestFocus()
    }
}
