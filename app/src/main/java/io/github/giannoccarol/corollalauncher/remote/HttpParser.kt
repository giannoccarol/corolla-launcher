package io.github.giannoccarol.corollalauncher.remote

import java.net.URLDecoder
import java.util.Locale

/** One request to the phone setup server. */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String = "",
) {
    val contentLength: Int get() = headers["content-length"]?.trim()?.toIntOrNull() ?: 0
}

/**
 * The little of HTTP/1.1 the setup page needs: a request line, headers, a form body. Pure,
 * unit tested; the socket reading and its size limits are in [SetupServer].
 */
object HttpParser {

    /** Headers larger than this are refused: a phone browser sends well under 2 KB. */
    const val MAX_HEAD_BYTES = 8 * 1024

    /** The setup form is a few hundred bytes; anything far bigger is not ours. */
    const val MAX_BODY_BYTES = 32 * 1024

    /** Parses everything before the blank line. Null when it is not a request we understand. */
    fun parseHead(head: String): HttpRequest? {
        val lines = head.split("\r\n").filter { it.isNotEmpty() }
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/")) return null
        val method = requestLine[0].uppercase(Locale.ROOT)
        if (method != "GET" && method != "POST") return null
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase(Locale.ROOT) to line.substring(colon + 1).trim()
        }.toMap()
        val (path, query) = splitTarget(requestLine[1])
        return HttpRequest(method, path, query, headers)
    }

    /** "/save?t=abc" into "/save" and its decoded query parameters. */
    fun splitTarget(target: String): Pair<String, Map<String, String>> {
        val question = target.indexOf('?')
        if (question < 0) return target to emptyMap()
        return target.substring(0, question) to parseForm(target.substring(question + 1))
    }

    /** application/x-www-form-urlencoded; a repeated name keeps its last value. */
    fun parseForm(encoded: String): Map<String, String> =
        encoded.split('&').filter { it.isNotEmpty() }.mapNotNull { pair ->
            val equals = pair.indexOf('=')
            val name = decode(if (equals < 0) pair else pair.substring(0, equals)) ?: return@mapNotNull null
            val value = decode(if (equals < 0) "" else pair.substring(equals + 1)) ?: return@mapNotNull null
            name to value
        }.toMap()

    /** Percent-decoding with + as space; null for a malformed escape. */
    fun decode(text: String): String? = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Text made safe inside HTML text and double-quoted attributes. */
    fun escapeHtml(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
