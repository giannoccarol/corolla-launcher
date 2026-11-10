package io.github.giannoccarol.corollalauncher.assistant

import org.json.JSONException
import org.json.JSONObject

enum class MediaCommand { PLAY, PAUSE, NEXT, PREVIOUS }

/** Things the assistant may ask the launcher to do. Anything else in a reply is ignored. */
sealed interface AssistantAction {
    data class Navigate(val destination: String) : AssistantAction
    data object NavigateHome : AssistantAction
    data object NavigateWork : AssistantAction
    data class Media(val command: MediaCommand) : AssistantAction
    data class OpenApp(val name: String) : AssistantAction

    /** Either an absolute [percent] or a relative [step] of -1 or +1. */
    data class Volume(val percent: Int?, val step: Int) : AssistantAction
    data class Theme(val dark: Boolean) : AssistantAction
}

/**
 * The model's answer: what to say and what to do. The model is asked for one JSON object; this
 * parser also copes with code fences, text around the object, and plain text (spoken as is).
 * Pure logic, unit tested.
 */
data class AssistantReply(val say: String, val actions: List<AssistantAction>) {

    companion object {

        fun parse(raw: String): AssistantReply {
            val text = raw.trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return AssistantReply(stripFences(text), emptyList())
            val json = try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: JSONException) {
                return AssistantReply(stripFences(text), emptyList())
            }
            val actions = json.optJSONArray("actions")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::action) }
            }.orEmpty()
            return AssistantReply(json.optString("say").trim(), actions)
        }

        private fun action(json: JSONObject): AssistantAction? = when (json.optString("type").lowercase()) {
            "navigate" -> json.optString("destination").trim().takeIf { it.isNotEmpty() }
                ?.let { AssistantAction.Navigate(it) }
            "navigate_home" -> AssistantAction.NavigateHome
            "navigate_work" -> AssistantAction.NavigateWork
            "media" -> when (json.optString("command").lowercase()) {
                "play", "resume" -> AssistantAction.Media(MediaCommand.PLAY)
                "pause", "stop" -> AssistantAction.Media(MediaCommand.PAUSE)
                "next", "skip" -> AssistantAction.Media(MediaCommand.NEXT)
                "previous", "back" -> AssistantAction.Media(MediaCommand.PREVIOUS)
                else -> null
            }
            "open_app" -> json.optString("app").trim().takeIf { it.isNotEmpty() }
                ?.let { AssistantAction.OpenApp(it) }
            "volume" -> when {
                json.has("percent") -> AssistantAction.Volume(json.optInt("percent").coerceIn(0, 100), 0)
                json.optString("change").lowercase() == "up" -> AssistantAction.Volume(null, 1)
                json.optString("change").lowercase() == "down" -> AssistantAction.Volume(null, -1)
                else -> null
            }
            "theme" -> when (json.optString("mode").lowercase()) {
                "dark" -> AssistantAction.Theme(dark = true)
                "light" -> AssistantAction.Theme(dark = false)
                else -> null
            }
            else -> null
        }

        private fun stripFences(text: String): String =
            text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }
}
