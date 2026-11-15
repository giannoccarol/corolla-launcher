package io.github.giannoccarol.corollalauncher.assistant.ai

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Model lists fetched from the providers, kept in memory for [TTL_MS] so opening the model
 * picker twice does not cost two requests. Keyed by provider and key, so a new key fetches
 * again. Nothing is stored on disk.
 */
object ModelCatalog {

    private const val TTL_MS = 30 * 60 * 1000L

    private class Entry(val at: Long, val models: List<ModelInfo>)

    private val cache = ConcurrentHashMap<String, Entry>()

    private fun key(provider: AiProvider, apiKey: String) = provider.id + "#" + apiKey.hashCode()

    /** The list fetched recently for this provider and key, if any. Any thread. */
    fun cached(provider: AiProvider, apiKey: String): List<ModelInfo>? =
        cache[key(provider, apiKey)]?.takeIf { SystemClock.elapsedRealtime() - it.at < TTL_MS }?.models

    /** The cached list or a fresh one. Blocking, background thread only; throws [LlmException]. */
    fun load(provider: AiProvider, apiKey: String): List<ModelInfo> {
        cached(provider, apiKey)?.let { return it }
        val models = LlmClient().listModels(provider, apiKey)
        cache[key(provider, apiKey)] = Entry(SystemClock.elapsedRealtime(), models)
        return models
    }
}
