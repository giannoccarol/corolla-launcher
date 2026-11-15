package io.github.giannoccarol.corollalauncher.assistant.ai

import androidx.annotation.StringRes
import io.github.giannoccarol.corollalauncher.BuildConfig
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.Transport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A provider error, with a message resource the UI can show and the provider's own detail. */
class LlmException(
    @StringRes val messageRes: Int,
    val formatArg: Any? = null,
    val detail: String? = null,
) : Exception(detail)

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val text: String)

/**
 * Minimal blocking HTTP client for four API families: Anthropic Messages, OpenAI-compatible
 * Chat Completions, OpenAI Responses and Google generateContent. Call it off the main thread.
 *
 * Ported from the owner's Monetine app (`app/monetine/ai/LlmClient.kt` and `LlmBodies.kt`),
 * text-only, using Android's built-in org.json instead of kotlinx.serialization so the
 * launcher adds no dependency. Anthropic requests follow the current API guidance: low effort
 * for quick spoken answers, server-side fallback on refusals, and a `refusal` check.
 */
class LlmClient {

    /** For the tool loop: one POST, parsed, with this client's error handling. */
    val transport = Transport { url, headers, body ->
        parse(request(url, headers, body))
    }

    /**
     * The provider's chat models, from `GET {base}/models` with the key ([ModelList] reads
     * the reply). Blocking: call it off the main thread, as [ModelCatalog] does.
     */
    fun listModels(provider: AiProvider, apiKey: String): List<ModelInfo> {
        val config = AiConfig(provider, apiKey, provider.defaultModel, sessionId = "models")
        val text = when (provider.style) {
            ApiStyle.ANTHROPIC -> request("${base(config)}/models?limit=1000", anthropicHeaders(config), null)
            else -> request("${base(config)}/models", bearer(config), null)
        }
        return ModelList.parse(text) ?: throw LlmException(R.string.llm_invalid_response, detail = text.take(MAX_DETAIL))
    }

    fun anthropicHeaders(config: AiConfig): Map<String, String> = buildMap {
        put("anthropic-version", "2023-06-01")
        put("x-api-key", config.apiKey)
        if (config.provider.id == "anthropic" && config.model in FALLBACK_MODELS) {
            put("anthropic-beta", "server-side-fallback-2026-07-01")
        }
    } + sessionHeaders(config)

    fun openAiHeaders(config: AiConfig): Map<String, String> = bearer(config)

    fun complete(config: AiConfig, system: String, messages: List<ChatMessage>): String =
        when (config.style) {
            ApiStyle.ANTHROPIC -> anthropic(config, system, messages)
            ApiStyle.OPENAI_COMPATIBLE -> chatCompletions(config, system, messages)
            ApiStyle.RESPONSES -> responses(config, system, messages)
            ApiStyle.GOOGLE -> google(config, system, messages)
        }

    // region Anthropic Messages

    private fun anthropic(config: AiConfig, system: String, messages: List<ChatMessage>): String {
        val firstParty = config.provider.id == "anthropic"
        val body = JSONObject().apply {
            put("model", config.model)
            // Thinking tokens count against max_tokens; leave room even for a one-line answer.
            put("max_tokens", ANTHROPIC_MAX_TOKENS)
            if (firstParty) {
                // The system prompt never changes within a conversation: cache it.
                put("system", JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", system)
                    put("cache_control", JSONObject().put("type", "ephemeral"))
                }))
                // Spoken answers must come quickly: lowest effort. Thinking stays adaptive.
                put("output_config", JSONObject().put("effort", "low"))
                if (config.model in FALLBACK_MODELS) put("fallbacks", "default")
            } else {
                put("system", system)
            }
            put("messages", messagesArray(messages, assistantRole = "assistant"))
        }
        val root = parse(request("${base(config)}/messages", anthropicHeaders(config), body))
        if (root.optString("stop_reason") == "refusal") throw LlmException(R.string.llm_refused)
        val content = root.optJSONArray("content") ?: JSONArray()
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
        return text.ifBlank { throw LlmException(R.string.llm_invalid_response) }
    }

    // endregion

    // region OpenAI-compatible, Responses, Google

    private fun chatCompletions(config: AiConfig, system: String, messages: List<ChatMessage>): String {
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                val rest = messagesArray(messages, assistantRole = "assistant")
                for (i in 0 until rest.length()) put(rest.get(i))
            })
        }
        val root = parse(request("${base(config)}/chat/completions", bearer(config), body))
        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw LlmException(R.string.llm_invalid_response, detail = errorOf(root))
        val text = when (val content = message.opt("content")) {
            is String -> content
            // Some providers return the content as a list of parts.
            is JSONArray -> buildString {
                for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
            }
            else -> ""
        }
        return text.ifBlank { throw LlmException(R.string.llm_invalid_response) }
    }

    private fun responses(config: AiConfig, system: String, messages: List<ChatMessage>): String {
        val body = JSONObject().apply {
            put("model", config.model)
            put("instructions", system)
            put("input", messagesArray(messages, assistantRole = "assistant"))
            put("store", false)
        }
        val root = parse(request("${base(config)}/responses", bearer(config), body))
        val output = root.optJSONArray("output") ?: JSONArray()
        val text = buildString {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                if (item.optString("type") != "message") continue
                val parts = item.optJSONArray("content") ?: continue
                for (j in 0 until parts.length()) {
                    val part = parts.optJSONObject(j) ?: continue
                    if (part.optString("type") == "output_text") append(part.optString("text"))
                }
            }
        }
        return text.ifBlank { throw LlmException(R.string.llm_invalid_response, detail = errorOf(root)) }
    }

    private fun google(config: AiConfig, system: String, messages: List<ChatMessage>): String {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().apply {
                for (message in messages) {
                    put(JSONObject().apply {
                        put("role", if (message.role == ChatRole.USER) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", message.text)))
                    })
                }
            })
        }
        val headers = mapOf("x-goog-api-key" to config.apiKey) + sessionHeaders(config)
        val root = parse(request("${base(config)}/models/${config.model}:generateContent", headers, body))
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
        return text.ifBlank { throw LlmException(R.string.llm_invalid_response, detail = errorOf(root)) }
    }

    // endregion

    private fun messagesArray(messages: List<ChatMessage>, assistantRole: String) = JSONArray().apply {
        for (message in messages) {
            put(JSONObject().apply {
                put("role", if (message.role == ChatRole.USER) "user" else assistantRole)
                put("content", message.text)
            })
        }
    }

    private fun bearer(config: AiConfig): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${config.apiKey}")
        if (config.provider.id == "openrouter") put("X-Title", "Corolla Launcher")
    } + sessionHeaders(config)

    /** OpenCode asks for a stable session id per conversation (routing and prompt caching). */
    private fun sessionHeaders(config: AiConfig): Map<String, String> =
        if (config.provider.baseUrl.contains(OPENCODE_HOST)) {
            mapOf("x-opencode-session" to config.sessionId, "x-opencode-client" to "corolla-launcher")
        } else {
            emptyMap()
        }

    private fun base(config: AiConfig) = config.provider.baseUrl.trimEnd('/')

    private fun parse(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw LlmException(R.string.llm_invalid_response, detail = text.take(MAX_DETAIL))
    }

    /** A POST with [body], or a GET when it is null. */
    private fun request(url: String, headers: Map<String, String>, body: JSONObject?): String {
        // Every catalogued endpoint is HTTPS; refuse anything else rather than send a key in clear.
        if (!url.startsWith("https://")) throw LlmException(R.string.llm_invalid_response, detail = url)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw httpError(status, text)
            return text
        } catch (e: LlmException) {
            throw e
        } catch (e: UnknownHostException) {
            throw LlmException(R.string.llm_no_connection)
        } catch (e: SocketTimeoutException) {
            throw LlmException(R.string.llm_timeout)
        } catch (e: IOException) {
            throw LlmException(R.string.llm_no_connection, detail = e.message)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(status: Int, body: String): LlmException {
        val detail = runCatching { errorOf(JSONObject(body)) }.getOrNull() ?: body.take(MAX_DETAIL)
        return when (status) {
            401, 403 -> LlmException(R.string.llm_http_401, detail = detail)
            404 -> LlmException(R.string.llm_http_404, detail = detail)
            408 -> LlmException(R.string.llm_timeout, detail = detail)
            429 -> LlmException(R.string.llm_http_429, detail = detail)
            in 500..599 -> LlmException(R.string.llm_http_5xx, status, detail)
            else -> LlmException(R.string.llm_http_other, status, detail)
        }
    }

    /** The provider's error message, whichever of the common shapes it uses. */
    private fun errorOf(root: JSONObject): String? {
        val error = root.opt("error")
        val text = when (error) {
            is JSONObject -> error.optString("message").ifBlank { error.optString("type") }
            is String -> error
            else -> root.optString("message").ifBlank { root.optString("detail") }
        }
        return text.takeIf { it.isNotBlank() }?.take(MAX_DETAIL)
    }

    private companion object {
        const val OPENCODE_HOST = "opencode.ai"
        const val ANTHROPIC_MAX_TOKENS = 4096
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_DETAIL = 220
        val USER_AGENT = "CorollaLauncher/${BuildConfig.VERSION_NAME} (Android)"

        /** Models that accept the server-side `fallbacks` parameter. */
        val FALLBACK_MODELS = setOf(
            "claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5",
        )
    }
}
