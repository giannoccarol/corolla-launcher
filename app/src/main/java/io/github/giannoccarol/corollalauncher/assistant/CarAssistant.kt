package io.github.giannoccarol.corollalauncher.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.ai.AiConfig
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.assistant.ai.ApiStyle
import io.github.giannoccarol.corollalauncher.assistant.ai.ChatMessage
import io.github.giannoccarol.corollalauncher.assistant.ai.ChatRole
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmClient
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.assistant.tools.CarTools
import io.github.giannoccarol.corollalauncher.settings.Prefs
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray

/** What the assistant knows about the car right now. Names only: no addresses, no history. */
data class CarContext(
    val localTime: String,
    val speedKmh: Int?,
    val tripKm: Double?,
    val nowPlaying: String?,
    val homeSaved: Boolean,
    val workSaved: Boolean,
    val darkTheme: Boolean,
    val appNames: List<String>,
    /** Latitude and longitude, used by the tools only; never put in the prompt itself. */
    val position: Pair<Double, Double>?,
    /** Label of the head unit's Android Auto / CarPlay app, when there is one. */
    val projectionApp: String? = null,
)

/**
 * One conversation with the driver's chosen AI provider. Requests run on a background thread;
 * results come back on the main thread. History is kept for the life of the panel only.
 *
 * Anthropic and OpenAI-compatible providers get real tools ([CarTools], plus Anthropic's web
 * search). Other protocols, and providers that reject tools, fall back to a JSON reply format
 * with the same actions but no lookups.
 */
class CarAssistant(context: Context) {

    private val prefs = Prefs(context)
    private val vault = KeyVault(context)
    private val client = LlmClient()
    private val main = Handler(Looper.getMainLooper())
    private val sessionId = UUID.randomUUID().toString()

    /** Tool conversations, in the provider's own wire format. */
    private var transcript = JSONArray()
    private var turns = 0

    /** Fallback conversation without tools. */
    private val history = mutableListOf<ChatMessage>()
    private var toolsRejected = false

    /** Null until a provider has an API key. */
    fun config(): AiConfig? {
        val provider = AiProviders.byId(prefs.aiProviderId)
        val key = vault.get(provider.id) ?: return null
        return AiConfig(provider, key, prefs.aiModel(provider.id) ?: provider.defaultModel, sessionId)
    }

    fun ask(
        text: String,
        car: CarContext,
        locale: Locale,
        onStep: (String) -> Unit,
        onReply: (AssistantReply) -> Unit,
        onError: (LlmException) -> Unit,
    ) {
        val config = config() ?: return
        val message = describe(car) + "\n\nDriver: " + text
        val languageName = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        executor.execute {
            try {
                val reply = if (usesTools(config)) {
                    withTools(config, car, locale, languageName, message, onStep)
                } else {
                    withoutTools(config, languageName, message)
                }
                main.post { onReply(reply) }
            } catch (e: LlmException) {
                main.post { onError(e) }
            } catch (e: RuntimeException) {
                main.post { onError(LlmException(R.string.llm_invalid_response, detail = e.message)) }
            }
        }
    }

    private fun usesTools(config: AiConfig): Boolean =
        !toolsRejected && (config.style == ApiStyle.ANTHROPIC || config.style == ApiStyle.OPENAI_COMPATIBLE)

    private fun withTools(
        config: AiConfig,
        car: CarContext,
        locale: Locale,
        languageName: String,
        message: String,
        onStep: (String) -> Unit,
    ): AssistantReply {
        // A fresh conversation after a while: always valid, and keeps requests small.
        if (turns >= MAX_TURNS) {
            transcript = JSONArray()
            turns = 0
        }
        val tools = CarTools(car, locale.language).apply { this.onStep = { main.post { onStep(it) } } }
        val agent = ToolAgent(client.transport, tools).apply { this.onStep = { main.post { onStep(it) } } }
        val attempt = JSONArray(transcript.toString())
        return try {
            val text = when (config.style) {
                ApiStyle.ANTHROPIC -> agent.anthropic(
                    config, client.anthropicHeaders(config), toolPrompt(languageName), attempt, message,
                    webSearch = config.provider.id == "anthropic", countryCode = locale.country,
                )
                else -> agent.openAi(config, client.openAiHeaders(config), toolPrompt(languageName), attempt, message)
            }
            transcript = attempt
            turns++
            AssistantReply(text, tools.actions.toList())
        } catch (e: LlmException) {
            // A provider that does not do tools answers 400 on the first request: fall back.
            if (turns == 0 && e.messageRes == R.string.llm_http_other && e.formatArg == HTTP_BAD_REQUEST &&
                config.style == ApiStyle.OPENAI_COMPATIBLE
            ) {
                toolsRejected = true
                withoutTools(config, languageName, message)
            } else {
                throw e
            }
        }
    }

    private fun withoutTools(config: AiConfig, languageName: String, message: String): AssistantReply {
        val user = ChatMessage(ChatRole.USER, message)
        val conversation = (history + user).takeLast(MAX_MESSAGES)
        val raw = client.complete(config, jsonPrompt(languageName), conversation)
        history += user
        history += ChatMessage(ChatRole.ASSISTANT, raw)
        return AssistantReply.parse(raw)
    }

    private fun describe(car: CarContext): String = buildString {
        appendLine("<car_state>")
        appendLine("Local time: ${car.localTime}")
        appendLine("Speed: ${car.speedKmh?.let { "$it km/h" } ?: "unknown (no GPS fix)"}")
        car.tripKm?.let { appendLine("Current trip: ${"%.1f".format(Locale.ROOT, it)} km") }
        appendLine("Position known: ${if (car.position != null) "yes" else "no"}")
        appendLine("Now playing: ${car.nowPlaying ?: "nothing"}")
        appendLine("Saved home address: ${if (car.homeSaved) "yes" else "no"}")
        appendLine("Saved work address: ${if (car.workSaved) "yes" else "no"}")
        appendLine("Theme: ${if (car.darkTheme) "dark" else "light"}")
        appendLine("Installed apps: ${car.appNames.joinToString(", ")}")
        appendLine("Android Auto / CarPlay app: ${car.projectionApp ?: "none installed"}")
        append("</car_state>")
    }

    private companion object {
        /** One worker is enough: requests never overlap in a conversation. */
        val executor = Executors.newSingleThreadExecutor()

        /** About four exchanges; older turns add latency and cost but rarely help in a car. */
        const val MAX_MESSAGES = 8
        const val MAX_TURNS = 6
        const val HTTP_BAD_REQUEST = 400

        private val CAR_INTRO = """
            You are the voice assistant of the head unit in a Toyota Corolla E120, talking to the driver while they drive.
            Your reply is read aloud, so keep it to one or two short sentences, with no lists, markdown, links or emoji, and never ask the driver to read or type anything.
            Each driver message starts with a <car_state> block describing the car right now.
            "Android Auto" or "CarPlay" means the head unit's projection app named in <car_state>: open that app.
        """.trimIndent()

        fun toolPrompt(languageName: String) = """
            $CAR_INTRO
            Answer in $languageName.

            Use the tools for anything live or local: weather, places, where the car is, and web search for news, scores, opening hours, prices or anything after your knowledge. Do not guess those.
            Act rather than ask: for "find me a petrol station", look nearby and pick the nearest suitable one; when the driver wants to go there, navigate to its coordinates. Ask a short question only when the choice really matters.
            Actions (navigate, music, apps, volume, theme) happen right after your reply, so confirm them briefly, for example "OK, heading to the Eni station, two kilometres away", in the driver's language.
            When a tool returns an error, say plainly what is missing. For calls, messages, climate or windows, say you cannot do that yet.
            Round numbers as people say them: "about 3 kilometres", "14 degrees".
        """.trimIndent()

        fun jsonPrompt(languageName: String) = """
            $CAR_INTRO
            Answer in $languageName, from your own knowledge and the <car_state> block.

            Reply with exactly one JSON object and nothing else:
            {"say": "<what to say aloud>", "actions": [<zero or more actions>]}

            Available actions:
            - {"type": "navigate", "destination": "<place or address>"}: start navigation there.
            - {"type": "navigate_home"} and {"type": "navigate_work"}: only when that address is saved.
            - {"type": "media", "command": "play" | "pause" | "next" | "previous"}
            - {"type": "open_app", "app": "<a name from Installed apps>"}
            - {"type": "volume", "percent": <0-100>} or {"type": "volume", "change": "up" | "down"}
            - {"type": "theme", "mode": "dark" | "light"}

            When the driver asks for something these actions cannot do, say briefly that you cannot do it yet.
            When an action does what was asked, keep "say" to a short confirmation.
        """.trimIndent()
    }
}
