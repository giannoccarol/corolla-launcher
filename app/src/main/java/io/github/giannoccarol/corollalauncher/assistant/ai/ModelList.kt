package io.github.giannoccarol.corollalauncher.assistant.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/** A model the provider offers, as its `GET /models` reports it. */
data class ModelInfo(val id: String, val displayName: String? = null) {
    /** What a list shows: the provider's own name when it has one, the id otherwise. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() && it != id } ?: id
}

/**
 * Reading a provider's model list. Every catalogued provider answers `GET {base}/models` with
 * either `{"data": [...]}` (OpenAI and compatible, Anthropic) or `{"models": [...]}`, items
 * carrying an `id` or a `name`; Gemini prefixes ids with "models/". Models that cannot chat
 * (embeddings, speech, images, moderation) are dropped.
 *
 * Ported from the owner's Monetine app (`ai/LlmClient.kt`, `listModels`), rewritten for
 * org.json. Pure, unit tested.
 */
object ModelList {

    private val NON_CHAT_HINTS = listOf(
        "embed", "embedding", "tts", "whisper", "dall-e", "moderation", "transcribe",
        "realtime", "image", "audio", "rerank", "guard", "veo", "imagen", "aqa",
    )

    /** Null when the text is not a model list. */
    fun parse(text: String): List<ModelInfo>? {
        val items: JSONArray = try {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("data") ?: root.optJSONArray("models") ?: return null
            }
        } catch (e: JSONException) {
            return null
        }
        return (0 until items.length())
            .mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").ifBlank { item.optString("name") }.removePrefix("models/")
                if (id.isBlank()) return@mapNotNull null
                val name = item.optString("display_name").ifBlank { item.optString("displayName") }
                    .ifBlank { item.optString("name").removePrefix("models/") }
                ModelInfo(id, name.takeIf { it.isNotBlank() && it != id })
            }
            .filterNot { model -> NON_CHAT_HINTS.any { model.id.contains(it, ignoreCase = true) } }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase(Locale.ROOT) }
    }

    /** Models whose id or name contains every word of [query], in any order and case. */
    fun search(models: List<ModelInfo>, query: String): List<ModelInfo> {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return models
        return models.filter { model ->
            val text = (model.id + " " + model.displayName.orEmpty()).lowercase(Locale.ROOT)
            words.all { text.contains(it) }
        }
    }
}
