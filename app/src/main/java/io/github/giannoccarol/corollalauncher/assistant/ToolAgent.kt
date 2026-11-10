package io.github.giannoccarol.corollalauncher.assistant

import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.ai.AiConfig
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.assistant.tools.CarTools
import io.github.giannoccarol.corollalauncher.assistant.tools.ToolBox
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/** Sends one JSON request and returns the parsed reply. [LlmClient] implements it. */
fun interface Transport {
    fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject
}

/**
 * The tool-use loop: ask, run the tools the model calls, send their results back, until the
 * model answers in text. Two wire formats: Anthropic Messages and OpenAI-compatible Chat
 * Completions. The transcript is kept in the wire format of the provider, so thinking blocks
 * and server tool results go back exactly as they came (the API requires that).
 *
 * Pure apart from the [Transport], so the loop is unit tested with a fake one.
 */
class ToolAgent(
    private val transport: Transport,
    private val tools: ToolBox,
    private val maxSteps: Int = MAX_STEPS,
) {

    /** Called with [CarTools.STEP_WEB] when a web search ran, for the panel's status line. */
    var onStep: (String) -> Unit = {}

    fun anthropic(
        config: AiConfig,
        headers: Map<String, String>,
        system: String,
        transcript: JSONArray,
        userText: String,
        webSearch: Boolean,
        countryCode: String?,
    ): String {
        transcript.put(JSONObject().put("role", "user").put("content", userText))
        val firstParty = config.provider.id == "anthropic"
        repeat(maxSteps) {
            val body = JSONObject().apply {
                put("model", config.model)
                // Thinking stays adaptive and counts against max_tokens: leave room.
                put("max_tokens", ANTHROPIC_MAX_TOKENS)
                if (firstParty) {
                    put("system", JSONArray().put(JSONObject().apply {
                        put("type", "text")
                        put("text", system)
                        put("cache_control", JSONObject().put("type", "ephemeral"))
                    }))
                    // Spoken answers must come quickly.
                    put("output_config", JSONObject().put("effort", "low"))
                    if (config.model in FALLBACK_MODELS) put("fallbacks", "default")
                } else {
                    put("system", system)
                }
                put("tools", anthropicTools(webSearch, countryCode))
                put("tool_choice", JSONObject().put("type", "auto"))
                put("messages", transcript)
            }
            val root = transport.post("${config.provider.baseUrl.trimEnd('/')}/messages", headers, body)
            val stop = root.optString("stop_reason")
            if (stop == "refusal") throw LlmException(R.string.llm_refused)
            val content = root.optJSONArray("content") ?: JSONArray()
            // The whole content goes back, thinking and server tool blocks included.
            transcript.put(JSONObject().put("role", "assistant").put("content", content))
            if (hasBlock(content, "server_tool_use")) onStep(CarTools.STEP_WEB)

            when (stop) {
                "tool_use" -> {
                    val results = JSONArray()
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        if (block.optString("type") != "tool_use") continue
                        val result = tools.run(block.optString("name"), block.optJSONObject("input") ?: JSONObject())
                        results.put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", block.optString("id"))
                            put("content", result.toString())
                            if (result.has("error")) put("is_error", true)
                        })
                    }
                    // All results of one turn in a single user message.
                    transcript.put(JSONObject().put("role", "user").put("content", results))
                }
                // A server tool (web search) is still working: send the same conversation again.
                "pause_turn" -> Unit
                else -> return textOf(content)
            }
        }
        throw LlmException(R.string.llm_too_many_steps)
    }

    fun openAi(
        config: AiConfig,
        headers: Map<String, String>,
        system: String,
        transcript: JSONArray,
        userText: String,
    ): String {
        if (transcript.length() == 0) transcript.put(JSONObject().put("role", "system").put("content", system))
        transcript.put(JSONObject().put("role", "user").put("content", userText))
        repeat(maxSteps) {
            val body = JSONObject().apply {
                put("model", config.model)
                put("messages", transcript)
                put("tools", openAiTools())
                put("tool_choice", "auto")
            }
            val root = transport.post("${config.provider.baseUrl.trimEnd('/')}/chat/completions", headers, body)
            val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw LlmException(R.string.llm_invalid_response)
            val calls = message.optJSONArray("tool_calls")
            transcript.put(JSONObject().apply {
                put("role", "assistant")
                put("content", message.opt("content") ?: JSONObject.NULL)
                if (calls != null && calls.length() > 0) put("tool_calls", calls)
            })
            if (calls == null || calls.length() == 0) return contentText(message.opt("content"))
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val function = call.optJSONObject("function") ?: continue
                val input = runCatching { JSONObject(function.optString("arguments").ifBlank { "{}" }) }
                    .getOrDefault(JSONObject())
                val result = tools.run(function.optString("name"), input)
                transcript.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", call.optString("id"))
                    put("content", result.toString())
                })
            }
        }
        throw LlmException(R.string.llm_too_many_steps)
    }

    private fun anthropicTools(webSearch: Boolean, countryCode: String?) = JSONArray().apply {
        tools.specs.forEach { spec ->
            put(JSONObject().apply {
                put("name", spec.name)
                put("description", spec.description)
                put("input_schema", spec.inputSchema)
            })
        }
        if (webSearch) {
            // Runs on Anthropic's servers with the same key; billed per search.
            put(JSONObject().apply {
                put("type", "web_search_20260209")
                put("name", "web_search")
                put("max_uses", MAX_SEARCHES)
                put("user_location", JSONObject().apply {
                    put("type", "approximate")
                    countryCode?.takeIf { it.length == 2 }?.let { put("country", it) }
                    put("timezone", TimeZone.getDefault().id)
                })
            })
        }
    }

    private fun openAiTools() = JSONArray().apply {
        tools.specs.forEach { spec ->
            put(JSONObject().put("type", "function").put("function", JSONObject().apply {
                put("name", spec.name)
                put("description", spec.description)
                put("parameters", spec.inputSchema)
            }))
        }
    }

    private fun hasBlock(content: JSONArray, type: String): Boolean =
        (0 until content.length()).any { content.optJSONObject(it)?.optString("type") == type }

    private fun textOf(content: JSONArray): String = buildString {
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }.trim().ifEmpty { throw LlmException(R.string.llm_invalid_response) }

    private fun contentText(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
        }
        else -> ""
    }.trim().ifEmpty { throw LlmException(R.string.llm_invalid_response) }

    companion object {
        /** Model calls per question: a lookup, an action and the answer, with room to spare. */
        const val MAX_STEPS = 5
        const val MAX_SEARCHES = 3
        const val ANTHROPIC_MAX_TOKENS = 4096

        /** Models that accept the server-side `fallbacks` parameter. */
        val FALLBACK_MODELS = setOf(
            "claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5",
        )
    }
}
