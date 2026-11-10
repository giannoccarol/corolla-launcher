package io.github.giannoccarol.corollalauncher.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.ai.AiConfig
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.assistant.ai.ChatMessage
import io.github.giannoccarol.corollalauncher.assistant.ai.ChatRole
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmClient
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.settings.Prefs
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

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
)

/**
 * One conversation with the driver's chosen AI provider. Requests run on a background thread;
 * results come back on the main thread. History is kept for the life of the panel only.
 */
class CarAssistant(context: Context) {

    private val prefs = Prefs(context)
    private val vault = KeyVault(context)
    private val client = LlmClient()
    private val main = Handler(Looper.getMainLooper())
    private val sessionId = UUID.randomUUID().toString()
    private val history = mutableListOf<ChatMessage>()

    /** Null until a provider has an API key. */
    fun config(): AiConfig? {
        val provider = AiProviders.byId(prefs.aiProviderId)
        val key = vault.get(provider.id) ?: return null
        return AiConfig(provider, key, prefs.aiModel(provider.id) ?: provider.defaultModel, sessionId)
    }

    fun ask(
        text: String,
        car: CarContext,
        languageName: String,
        onReply: (AssistantReply) -> Unit,
        onError: (LlmException) -> Unit,
    ) {
        val config = config() ?: return
        val message = ChatMessage(ChatRole.USER, describe(car) + "\n\nDriver: " + text)
        val conversation = (history + message).takeLast(MAX_MESSAGES)
        executor.execute {
            try {
                val raw = client.complete(config, systemPrompt(languageName), conversation)
                main.post {
                    history += message
                    history += ChatMessage(ChatRole.ASSISTANT, raw)
                    onReply(AssistantReply.parse(raw))
                }
            } catch (e: LlmException) {
                main.post { onError(e) }
            } catch (e: RuntimeException) {
                main.post { onError(LlmException(R.string.llm_invalid_response, detail = e.message)) }
            }
        }
    }

    private fun describe(car: CarContext): String = buildString {
        appendLine("<car_state>")
        appendLine("Local time: ${car.localTime}")
        appendLine("Speed: ${car.speedKmh?.let { "$it km/h" } ?: "unknown (no GPS fix)"}")
        car.tripKm?.let { appendLine("Current trip: ${"%.1f".format(Locale.ROOT, it)} km") }
        appendLine("Now playing: ${car.nowPlaying ?: "nothing"}")
        appendLine("Saved home address: ${if (car.homeSaved) "yes" else "no"}")
        appendLine("Saved work address: ${if (car.workSaved) "yes" else "no"}")
        appendLine("Theme: ${if (car.darkTheme) "dark" else "light"}")
        appendLine("Installed apps: ${car.appNames.joinToString(", ")}")
        append("</car_state>")
    }

    private companion object {
        /** One worker is enough: requests never overlap in a conversation. */
        val executor = Executors.newSingleThreadExecutor()

        /** About four exchanges; older turns add latency and cost but rarely help in a car. */
        const val MAX_MESSAGES = 8

        fun systemPrompt(languageName: String) = """
            You are the voice assistant of the head unit in a Toyota Corolla E120, talking to the driver while they drive.
            Answer in $languageName. Your reply is read aloud, so keep it to one or two short sentences, with no lists, markdown or emoji, and never ask the driver to read or type anything.
            You can answer general questions from your own knowledge, and questions about the car from the <car_state> block in each message.

            Reply with exactly one JSON object and nothing else:
            {"say": "<what to say aloud>", "actions": [<zero or more actions>]}

            Available actions:
            - {"type": "navigate", "destination": "<place or address>"}: start navigation there.
            - {"type": "navigate_home"} and {"type": "navigate_work"}: only when that address is saved.
            - {"type": "media", "command": "play" | "pause" | "next" | "previous"}
            - {"type": "open_app", "app": "<a name from Installed apps>"}
            - {"type": "volume", "percent": <0-100>} or {"type": "volume", "change": "up" | "down"}
            - {"type": "theme", "mode": "dark" | "light"}

            When the driver asks for something these actions cannot do (calls, messages, climate, windows), say briefly that you cannot do it yet.
            When an action does what was asked, keep "say" to a short confirmation.
        """.trimIndent()
    }
}
