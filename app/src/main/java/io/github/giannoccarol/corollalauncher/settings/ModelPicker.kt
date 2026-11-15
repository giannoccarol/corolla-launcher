package io.github.giannoccarol.corollalauncher.settings

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProvider
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.assistant.ai.ModelCatalog
import io.github.giannoccarol.corollalauncher.assistant.ai.ModelInfo
import io.github.giannoccarol.corollalauncher.assistant.ai.ModelList
import io.github.giannoccarol.corollalauncher.ui.TextPrompt

/**
 * Settings → Model, like pi-desktop's: the provider's own list of models, fetched with the
 * saved key and searchable, instead of typing a model id by hand. It opens at once with the
 * suggested models and fills in when the list arrives; without a key or a connection the
 * suggestions stay, and a name can still be typed.
 */
class ModelPicker(
    private val activity: Activity,
    private val provider: AiProvider,
    private val apiKey: String?,
    private val current: String?,
    private val onPicked: (String?) -> Unit,
) {

    /** The first row: the provider's default (stored as no model). */
    private val defaultRow = ModelInfo("", activity.getString(R.string.model_default, provider.defaultModel))

    private var all: List<ModelInfo> = withCurrent(provider.suggestedModels.map { ModelInfo(it) })
    private var shown: List<ModelInfo> = all
    private val main = Handler(Looper.getMainLooper())
    private var listView: ListView? = null

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_model_picker, null)
        val search = view.findViewById<EditText>(R.id.search)
        val status = view.findViewById<TextView>(R.id.status)
        val list = view.findViewById<ListView>(R.id.list)
        val adapter = Adapter()
        list.adapter = adapter
        listView = list
        scrollToCurrent()

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.model_picker_title, provider.name))
            .setView(view)
            .setNeutralButton(R.string.settings_ai_model_other) { _, _ -> typeName() }
            .setNegativeButton(R.string.cancel, null)
            .create()

        list.setOnItemClickListener { _, _, position, _ ->
            val picked = rowAt(position)
            dialog.dismiss()
            onPicked(picked.id.ifEmpty { null })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {
                shown = ModelList.search(all, text?.toString().orEmpty())
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        if (apiKey.isNullOrBlank()) {
            status.setText(R.string.model_needs_key)
        } else {
            val cached = ModelCatalog.cached(provider, apiKey)
            if (cached != null) {
                show(cached, adapter, search)
                status.visibility = View.GONE
            } else {
                status.setText(R.string.model_loading)
                load(apiKey, adapter, search, status)
            }
        }
        dialog.show()
    }

    private fun load(key: String, adapter: Adapter, search: EditText, status: TextView) {
        Thread({
            val result = try {
                Result.success(ModelCatalog.load(provider, key))
            } catch (e: LlmException) {
                Result.failure(e)
            }
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        status.setText(R.string.model_list_empty)
                    } else {
                        show(models, adapter, search)
                        status.visibility = View.GONE
                    }
                }.onFailure { error ->
                    val reason = (error as LlmException).let { activity.getString(it.messageRes, it.formatArg) }
                    status.text = activity.getString(R.string.model_list_failed, reason)
                }
            }
        }, "model-list").apply { isDaemon = true }.start()
    }

    private fun show(models: List<ModelInfo>, adapter: Adapter, search: EditText) {
        all = withCurrent(models)
        shown = ModelList.search(all, search.text.toString())
        adapter.notifyDataSetChanged()
        scrollToCurrent()
    }

    /** Lists run to hundreds of models: open on the one in use. */
    private fun scrollToCurrent() {
        val index = shown.indexOfFirst { it.id == current }
        if (index >= 0) listView?.setSelection(index + 1)
    }

    /** A model chosen earlier stays selectable even if the provider no longer lists it. */
    private fun withCurrent(models: List<ModelInfo>): List<ModelInfo> =
        if (current.isNullOrBlank() || models.any { it.id == current }) models else listOf(ModelInfo(current)) + models

    private fun typeName() {
        TextPrompt.show(
            activity, activity.getString(R.string.settings_ai_model), R.string.settings_ai_model_hint,
            current, R.string.save, password = false,
        ) { onPicked(it.ifEmpty { null }) }
    }

    /** Row 0 is the default, then the models matching the search. */
    private fun rowAt(position: Int): ModelInfo = if (position == 0) defaultRow else shown[position - 1]

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = shown.size + 1
        override fun getItem(position: Int) = rowAt(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(activity).inflate(R.layout.item_model, parent, false)
            val model = rowAt(position)
            val selected = if (position == 0) current.isNullOrBlank() else model.id == current
            val name = row.findViewById<TextView>(R.id.name)
            val id = row.findViewById<TextView>(R.id.modelId)
            name.text = model.label
            name.setTextColor(ContextCompat.getColor(activity, if (selected) R.color.accent else R.color.text_primary))
            id.text = model.id
            id.visibility = if (model.label != model.id && model.id.isNotEmpty()) View.VISIBLE else View.GONE
            return row
        }
    }
}
