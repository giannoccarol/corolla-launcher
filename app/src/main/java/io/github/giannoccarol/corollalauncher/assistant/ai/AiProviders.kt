package io.github.giannoccarol.corollalauncher.assistant.ai

/**
 * The AI providers the assistant can talk to, one API key each.
 *
 * Adapted from the provider catalogue of the owner's Monetine app (`app/monetine/ai/AiProviders.kt`),
 * itself aligned with pi-desktop; the custom local endpoint is left out, since a car has no LAN.
 */
enum class ApiStyle { OPENAI_COMPATIBLE, ANTHROPIC, RESPONSES, GOOGLE }

data class AiProvider(
    val id: String,
    val name: String,
    val style: ApiStyle,
    val baseUrl: String,
    val keyUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    /**
     * Gateways that serve each model with its maker's protocol (OpenCode Zen/Go): the protocol is
     * guessed from the model name.
     */
    val multiProtocol: Boolean = false,
) {
    fun styleFor(model: String): ApiStyle {
        if (!multiProtocol) return style
        val id = model.lowercase()
        return when {
            id.startsWith("claude") -> ApiStyle.ANTHROPIC
            id.startsWith("gemini") -> ApiStyle.GOOGLE
            id.startsWith("gpt") || id.startsWith("grok") -> ApiStyle.RESPONSES
            else -> ApiStyle.OPENAI_COMPATIBLE
        }
    }
}

object AiProviders {

    const val DEFAULT_ID = "anthropic"

    val all: List<AiProvider> = listOf(
        AiProvider(
            "anthropic", "Anthropic", ApiStyle.ANTHROPIC, "https://api.anthropic.com/v1",
            "https://console.anthropic.com/settings/keys", "claude-opus-5-5",
            listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        ),
        AiProvider(
            "openai", "OpenAI", ApiStyle.OPENAI_COMPATIBLE, "https://api.openai.com/v1",
            "https://platform.openai.com/api-keys", "gpt-5-mini", listOf("gpt-5-mini", "gpt-5"),
        ),
        AiProvider(
            "google", "Google Gemini", ApiStyle.OPENAI_COMPATIBLE,
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "https://aistudio.google.com/apikey", "gemini-2.5-flash",
            listOf("gemini-2.5-flash", "gemini-2.5-pro"),
        ),
        AiProvider(
            "openrouter", "OpenRouter", ApiStyle.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1",
            "https://openrouter.ai/keys", "openrouter/auto", listOf("openrouter/auto"),
        ),
        AiProvider(
            "xai", "xAI", ApiStyle.OPENAI_COMPATIBLE, "https://api.x.ai/v1",
            "https://console.x.ai", "grok-3-mini", listOf("grok-3-mini"),
        ),
        AiProvider(
            "deepseek", "DeepSeek", ApiStyle.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1",
            "https://platform.deepseek.com/api_keys", "deepseek-chat", listOf("deepseek-chat"),
        ),
        AiProvider(
            "mistral", "Mistral", ApiStyle.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1",
            "https://console.mistral.ai/api-keys", "mistral-small-latest",
            listOf("mistral-small-latest", "mistral-large-latest"),
        ),
        AiProvider(
            "groq", "Groq", ApiStyle.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1",
            "https://console.groq.com/keys", "llama-3.3-70b-versatile",
            listOf("llama-3.3-70b-versatile"),
        ),
        AiProvider(
            "cerebras", "Cerebras", ApiStyle.OPENAI_COMPATIBLE, "https://api.cerebras.ai/v1",
            "https://cloud.cerebras.ai", "llama-3.3-70b", listOf("llama-3.3-70b"),
        ),
        AiProvider(
            "together", "Together AI", ApiStyle.OPENAI_COMPATIBLE, "https://api.together.xyz/v1",
            "https://api.together.ai/settings/api-keys", "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        ),
        AiProvider(
            "fireworks", "Fireworks", ApiStyle.OPENAI_COMPATIBLE,
            "https://api.fireworks.ai/inference/v1", "https://fireworks.ai/account/api-keys",
            "accounts/fireworks/models/llama-v3p3-70b-instruct",
            listOf("accounts/fireworks/models/llama-v3p3-70b-instruct"),
        ),
        AiProvider(
            "nvidia", "NVIDIA NIM", ApiStyle.OPENAI_COMPATIBLE, "https://integrate.api.nvidia.com/v1",
            "https://build.nvidia.com", "meta/llama-3.3-70b-instruct",
            listOf("meta/llama-3.3-70b-instruct"),
        ),
        AiProvider(
            "huggingface", "Hugging Face", ApiStyle.OPENAI_COMPATIBLE,
            "https://router.huggingface.co/v1", "https://huggingface.co/settings/tokens",
            "meta-llama/Llama-3.3-70B-Instruct", listOf("meta-llama/Llama-3.3-70B-Instruct"),
        ),
        AiProvider(
            "opencode", "OpenCode Zen", ApiStyle.OPENAI_COMPATIBLE, "https://opencode.ai/zen/v1",
            "https://opencode.ai/zen", "deepseek-v4-flash",
            listOf("deepseek-v4-flash", "kimi-k3", "claude-haiku-4-5", "gpt-5.4-mini"),
            multiProtocol = true,
        ),
        AiProvider(
            "opencode-go", "OpenCode Go", ApiStyle.OPENAI_COMPATIBLE, "https://opencode.ai/zen/go/v1",
            "https://opencode.ai/zen/go", "deepseek-v4.1-flash",
            listOf("deepseek-v4.1-flash", "kimi-k3", "glm-5.3-flash"),
            multiProtocol = true,
        ),
        AiProvider(
            "kimi-coding", "Kimi For Coding", ApiStyle.ANTHROPIC, "https://api.kimi.com/coding/v1",
            "https://www.kimi.com/coding", "k3", listOf("k3", "k3-256k"),
        ),
        AiProvider(
            "moonshot", "Kimi (Moonshot)", ApiStyle.OPENAI_COMPATIBLE, "https://api.moonshot.ai/v1",
            "https://platform.moonshot.ai/console/api-keys", "moonshot-v1-8k",
            listOf("moonshot-v1-8k"),
        ),
        AiProvider(
            "minimax", "MiniMax", ApiStyle.OPENAI_COMPATIBLE, "https://api.minimax.io/v1",
            "https://www.minimax.io/platform", "MiniMax-M2", listOf("MiniMax-M2"),
        ),
    )

    fun byId(id: String?): AiProvider = all.firstOrNull { it.id == id } ?: all.first()
}

/** Everything one request needs. */
data class AiConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    /** Stable per conversation; OpenCode routes and caches by it. */
    val sessionId: String,
) {
    val style: ApiStyle get() = provider.styleFor(model)
}
