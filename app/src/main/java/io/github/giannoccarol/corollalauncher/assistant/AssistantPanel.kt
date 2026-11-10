package io.github.giannoccarol.corollalauncher.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.View
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.assistant.ai.LlmException
import io.github.giannoccarol.corollalauncher.databinding.ViewAssistantBinding
import io.github.giannoccarol.corollalauncher.panels.Sheet
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.TextPrompt
import java.util.Locale

/** The launcher side of the assistant: car state for the prompt, and the actions it carries out. */
interface AssistantHost {
    fun carContext(): CarContext

    /** Runs [action]; returns a message to show when it could not be done. */
    fun perform(action: AssistantAction): String?

    fun askForMicrophone()
    fun openSettings()
}

/**
 * Talk to the assistant: opens listening straight away when it can, shows what was heard and
 * the reply, reads the reply aloud, and carries out the actions. Typing is the fallback for a
 * unit whose microphone or speech recogniser is missing.
 */
class AssistantPanel(
    activity: Activity,
    private val host: AssistantHost,
    private val speaker: Speaker,
) : Sheet(activity, R.string.assistant_title) {

    private enum class State { IDLE, LISTENING, THINKING, ANSWERED, ERROR }

    private val assistant = CarAssistant(activity)
    private val speech = SpeechController(activity)
    private val prefs = Prefs(activity)
    private var view: ViewAssistantBinding? = null

    private var state = State.IDLE
    private var heard: String? = null
    private var reply: String? = null
    private var detail: String? = null
    private var status: String? = null

    override fun build() {
        val binding = ViewAssistantBinding.inflate(inflater, content, false)
        view = binding
        content.addView(binding.root)
        binding.mic.setOnClickListener { onMic() }
        binding.type.setOnClickListener { typeQuestion() }
        binding.settings.setOnClickListener {
            dismiss()
            host.openSettings()
        }
        Motion.pressable(binding.mic, binding.type, binding.settings)
        update()
    }

    override fun onShown() {
        // Voice first: a panel opened from the steering wheel button should just listen.
        if (assistant.config() != null && hasMicrophone() && speech.isAvailable()) {
            content.post { startListening() }
        }
    }

    override fun onDismissed() {
        speech.release()
    }

    private fun onMic() {
        when {
            assistant.config() == null -> update()
            state == State.LISTENING -> speech.stop()
            !hasMicrophone() -> host.askForMicrophone()
            !speech.isAvailable() -> fail(R.string.assistant_speech_unavailable)
            else -> startListening()
        }
    }

    private fun startListening() {
        speaker.stop()
        state = State.LISTENING
        heard = null
        detail = null
        status = activity.getString(R.string.assistant_listening)
        update()
        speech.start(languageTag()) { event ->
            when (event) {
                SpeechEvent.Ready -> Unit
                is SpeechEvent.Level -> pulse(event.value)
                is SpeechEvent.Partial -> {
                    heard = event.text
                    update()
                }
                is SpeechEvent.Final -> send(event.text)
                is SpeechEvent.Error -> if (event.silent) idle() else fail(event.message)
            }
        }
    }

    private fun typeQuestion() {
        if (assistant.config() == null) return update()
        speech.release()
        TextPrompt.show(
            activity, activity.getString(R.string.assistant_title), R.string.assistant_type_hint,
            initial = null, positiveRes = R.string.assistant_send, password = false,
        ) { text -> if (text.isNotBlank()) send(text) }
    }

    private fun send(text: String) {
        heard = text
        state = State.THINKING
        status = activity.getString(R.string.assistant_thinking)
        pulse(0f)
        update()
        assistant.ask(text, host.carContext(), languageName(), ::onReply, ::onError)
    }

    private fun onReply(answer: AssistantReply) {
        if (!isShowing) return
        val problems = mutableListOf<String>()
        // Theme last: it recreates the activity, which closes this panel.
        val (theme, others) = answer.actions.partition { it is AssistantAction.Theme }
        others.forEach { action -> host.perform(action)?.let(problems::add) }

        val spoken = (listOf(answer.say) + problems).filter { it.isNotBlank() }.joinToString(" ")
        reply = spoken
        state = State.ANSWERED
        status = activity.getString(R.string.assistant_tap_to_talk)
        update()
        if (prefs.speakReplies && spoken.isNotBlank()) speaker.say(spoken, locale())

        val leavesLauncher = others.any {
            it is AssistantAction.Navigate || it is AssistantAction.NavigateHome ||
                it is AssistantAction.NavigateWork || it is AssistantAction.OpenApp
        }
        if (theme.isNotEmpty()) {
            dismiss()
            theme.forEach { host.perform(it) }
        } else if (leavesLauncher && problems.isEmpty()) {
            dismiss()
        }
    }

    private fun onError(error: LlmException) {
        if (!isShowing) return
        val message = if (error.formatArg != null) {
            activity.getString(error.messageRes, error.formatArg)
        } else {
            activity.getString(error.messageRes)
        }
        fail(message, error.detail)
    }

    private fun fail(messageRes: Int) = fail(activity.getString(messageRes), null)

    private fun fail(message: String, providerDetail: String?) {
        state = State.ERROR
        reply = message
        detail = providerDetail
        status = activity.getString(R.string.assistant_tap_to_talk)
        pulse(0f)
        update()
    }

    private fun idle() {
        state = State.IDLE
        status = activity.getString(R.string.assistant_tap_to_talk)
        pulse(0f)
        update()
    }

    private fun update() {
        val binding = view ?: return
        val configured = assistant.config() != null
        binding.settings.visibility = if (configured) View.GONE else View.VISIBLE
        binding.type.visibility = if (configured) View.VISIBLE else View.GONE
        binding.mic.isEnabled = configured
        binding.mic.alpha = if (configured) 1f else DISABLED_ALPHA
        binding.mic.setImageResource(if (state == State.LISTENING) R.drawable.ic_stop else R.drawable.ic_mic_large)
        binding.mic.contentDescription = activity.getString(
            if (state == State.LISTENING) R.string.assistant_stop else R.string.assistant_talk,
        )

        binding.heard.text = heard?.let { "“$it”" }
        binding.heard.visibility = if (heard.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.reply.text = when {
            !configured -> activity.getString(R.string.assistant_no_key)
            reply != null -> reply
            !hasMicrophone() -> activity.getString(R.string.assistant_mic_needed)
            else -> activity.getString(R.string.assistant_greeting)
        }
        binding.detail.text = detail
        binding.detail.visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.status.text = when {
            !configured -> ""
            else -> status ?: activity.getString(R.string.assistant_tap_to_talk)
        }
    }

    /** The halo grows with the voice level: one scale animation, nothing redrawn. */
    private fun pulse(level: Float) {
        val halo = view?.micHalo ?: return
        val scale = 1f + level * HALO_GROWTH
        if (Motion.enabled(activity)) {
            halo.animate().scaleX(scale).scaleY(scale).setDuration(HALO_MS).start()
        } else {
            halo.scaleX = scale
            halo.scaleY = scale
        }
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun locale(): Locale = activity.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun languageTag(): String = locale().toLanguageTag()

    private fun languageName(): String = locale().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
        const val HALO_GROWTH = 0.18f
        const val HALO_MS = 90L
    }
}
