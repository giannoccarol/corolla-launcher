package io.github.giannoccarol.corollalauncher.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.apps.AppIcons
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.apps.IconPack
import io.github.giannoccarol.corollalauncher.assistant.KeyVault
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.phone.Favorites
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * A tiny HTTP server for the phone setup page, alive only while *Settings → Set up from your
 * phone* is open (and at most [IDLE_MS] after the last request). One connection at a time on
 * its own thread; every request must carry the session's [SetupToken]. Settings are written
 * on that thread (SharedPreferences and the key vault are thread-safe); what must happen on
 * the main thread (night mode) is posted there.
 */
object SetupServer {

    data class Session(val url: String, val token: String)

    sealed interface Event {
        data object Opened : Event
        data class Saved(val atMillis: Long) : Event
        data object Stopped : Event
    }

    /** Preferred port; any free one is used if it is taken. */
    private const val PORT = 8642
    private const val IDLE_MS = 10 * 60 * 1000L
    private const val SOCKET_TIMEOUT_MS = 10_000
    private const val MAX_BAD_TOKENS = 20
    private const val TAG = "SetupServer"

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var server: ServerSocket? = null
    @Volatile var session: Session? = null
        private set
    @Volatile private var lastUse = 0L
    @Volatile private var badTokens = 0

    private lateinit var appContext: Context
    private lateinit var page: SetupPage

    /** Main thread only. Events from the server are delivered on the main thread. */
    var listener: ((Event) -> Unit)? = null

    /** The latest Opened or Saved event of this session, for a screen recreated meanwhile. */
    var lastEvent: Event? = null
        private set

    /**
     * Starts a session, or returns the running one. [localized] carries the interface language
     * (it must not be an activity: the server outlives screen rotations and theme changes).
     * Null when the unit has no address on a local network or no port is free.
     */
    fun start(localized: Context, palette: SetupPage.Palette): Session? {
        session?.let { return it }
        val address = LocalAddress.find() ?: return null
        val socket = try {
            ServerSocket(PORT)
        } catch (e: IOException) {
            try {
                ServerSocket(0)
            } catch (e2: IOException) {
                Log.w(TAG, "No port for the setup server", e2)
                return null
            }
        }
        appContext = localized
        page = SetupPage(localized, palette)
        val token = SetupToken.generate()
        val started = Session("http://$address:${socket.localPort}/?t=$token", token)
        server = socket
        session = started
        lastUse = SystemClock.elapsedRealtime()
        badTokens = 0
        lastEvent = null
        Thread({ serve(socket, token) }, "phone-setup").apply { isDaemon = true }.start()
        main.postDelayed(idleCheck, IDLE_MS)
        return started
    }

    fun stop() {
        main.removeCallbacks(idleCheck)
        val socket = server ?: return
        server = null
        session = null
        try {
            socket.close()
        } catch (e: IOException) {
            // Closing is all that matters.
        }
        notify(Event.Stopped)
    }

    private val idleCheck = object : Runnable {
        override fun run() {
            val idle = SystemClock.elapsedRealtime() - lastUse
            if (idle >= IDLE_MS) stop() else main.postDelayed(this, IDLE_MS - idle)
        }
    }

    private fun notify(event: Event) = main.post {
        if (event !is Event.Stopped) lastEvent = event
        listener?.invoke(event)
    }

    private fun serve(socket: ServerSocket, token: String) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: SocketException) {
                return // Closed by stop().
            } catch (e: IOException) {
                continue
            }
            try {
                client.soTimeout = SOCKET_TIMEOUT_MS
                handle(client, token)
            } catch (e: IOException) {
                // A dropped or slow connection: the phone simply retries.
            } catch (e: RuntimeException) {
                Log.w(TAG, "Setup request failed", e)
            } finally {
                try {
                    client.close()
                } catch (e: IOException) {
                    // Nothing to do.
                }
            }
        }
    }

    private fun handle(client: Socket, token: String) {
        val input = client.getInputStream()
        val head = readHead(input) ?: return respond(client, 400, "")
        val request = HttpParser.parseHead(head) ?: return respond(client, 400, "")
        if (request.contentLength > HttpParser.MAX_BODY_BYTES) return respond(client, 413, "")
        val body = if (request.method == "POST") readBody(input, request.contentLength) else ""
        val form = if (request.method == "POST") HttpParser.parseForm(body) else emptyMap()

        val given = if (request.method == "POST") form["t"] else request.query["t"]
        val known = request.path == "/" || request.path == "/save"
        if (!known) return respond(client, 404, page.expired())
        if (!SetupToken.matches(token, given)) {
            badTokens++
            respond(client, 403, page.expired())
            // Someone is guessing: close the door rather than keep answering.
            if (badTokens >= MAX_BAD_TOKENS) main.post { stop() }
            return
        }
        lastUse = SystemClock.elapsedRealtime()

        if (request.method == "GET") {
            notify(Event.Opened)
            return respond(client, 200, page.form(token, currentValues(), choices()))
        }
        val result = SetupForm.parse(form, currentValues(), choices().providers.map { it.id }.toSet(), iconStyleIds())
        apply(result.values)
        notify(Event.Saved(System.currentTimeMillis()))
        val banner = if (result.problems.isEmpty()) SetupPage.Banner.SAVED else SetupPage.Banner.PROBLEMS
        respond(client, 200, page.form(token, currentValues(), choices(), result.problems, banner))
    }

    private fun currentValues(): SetupValues {
        val prefs = Prefs(appContext)
        val provider = AiProviders.byId(prefs.aiProviderId)
        return SetupValues(
            driverName = prefs.driverName,
            homeAddress = prefs.homeAddress,
            workAddress = prefs.workAddress,
            favorites = (0 until Favorites.SLOTS).map(prefs::favorite),
            themeStyle = prefs.themeStyle,
            themeMode = prefs.themeMode,
            font = prefs.fontChoice,
            uiSize = prefs.uiSize,
            iconStyle = prefs.iconStyle,
            providerId = provider.id,
            model = prefs.aiModel(provider.id),
        )
    }

    private fun apply(values: SetupValues) {
        val prefs = Prefs(appContext)
        val before = currentValues()
        prefs.driverName = values.driverName
        prefs.homeAddress = values.homeAddress
        prefs.workAddress = values.workAddress
        values.favorites.forEachIndexed { slot, favorite -> prefs.setFavorite(slot, favorite) }
        prefs.themeStyle = values.themeStyle
        prefs.fontChoice = values.font
        prefs.uiSize = values.uiSize
        prefs.iconStyle = values.iconStyle
        prefs.aiProviderId = values.providerId
        prefs.setAiModel(values.providerId, values.model)
        val vault = KeyVault(appContext)
        when {
            values.removeApiKey -> vault.remove(values.providerId)
            values.newApiKey != null -> vault.set(values.providerId, values.newApiKey)
        }
        if (values.themeMode != before.themeMode) {
            prefs.themeMode = values.themeMode
            main.post { ThemeManager.apply(appContext) }
        }
        // Icons are rendered for a style and a size: redo them when either changed.
        if (values.iconStyle != before.iconStyle || values.uiSize != before.uiSize) {
            AppRepository.reload(appContext)
        }
    }

    private fun choices(): SetupPage.Choices {
        val vault = KeyVault(appContext)
        val packs = IconPack.installed(appContext)
        return SetupPage.Choices(
            iconStyles = listOf(
                SetupPage.Option(AppIcons.STYLE_THEMED, appContext.getString(R.string.settings_icons_themed)),
                SetupPage.Option(AppIcons.STYLE_MONO, appContext.getString(R.string.settings_icons_mono)),
                SetupPage.Option(AppIcons.STYLE_ORIGINAL, appContext.getString(R.string.settings_icons_original)),
            ) + packs.map { SetupPage.Option(it.packageName, it.label) },
            providers = AiProviders.all.map { SetupPage.Provider(it.id, it.name, it.suggestedModels, it.defaultModel) },
            keyEndings = AiProviders.all.mapNotNull { provider -> vault.get(provider.id)?.let { provider.id to it.takeLast(4) } }.toMap(),
        )
    }

    private fun iconStyleIds(): Set<String> =
        setOf(AppIcons.STYLE_THEMED, AppIcons.STYLE_MONO, AppIcons.STYLE_ORIGINAL) +
            IconPack.installed(appContext).map { it.packageName }

    /** Bytes up to the blank line, at most [HttpParser.MAX_HEAD_BYTES]; null if longer or cut. */
    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (buffer.size() < HttpParser.MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            if (matched == end.size) return buffer.toString("ISO-8859-1")
        }
        return null
    }

    private fun readBody(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun respond(client: Socket, status: Int, html: String) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 413 -> "Payload Too Large"
            else -> "Error"
        }
        val body = html.toByteArray(Charsets.UTF_8)
        // The token travels in the address: never let it leak through a referrer or a cache.
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\n" +
            "Connection: close\r\n\r\n"
        val output = client.getOutputStream()
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }
}
