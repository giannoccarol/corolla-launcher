package io.github.giannoccarol.corollalauncher.assistant.tools

import io.github.giannoccarol.corollalauncher.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small blocking GET for the assistant's tools. Call off the main thread. */
object Http {

    /**
     * A generic, honest client name: OpenStreetMap's Nominatim policy asks every app to identify
     * itself. No personal data goes in it.
     */
    val USER_AGENT = "CorollaLauncher/${BuildConfig.VERSION_NAME} (Android head unit launcher)"

    private const val TIMEOUT_MS = 10_000

    fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status from ${URL(url).host}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
