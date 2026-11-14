package io.github.giannoccarol.corollalauncher.remote

import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.DateFormat
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.databinding.ActivityPhoneSetupBinding
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.theme.LauncherTheme
import io.github.giannoccarol.corollalauncher.ui.LauncherActivity
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.UiScale
import io.nayuki.qrcodegen.QrCode
import java.util.Date

/**
 * *Settings → Set up from your phone*. Shows a QR code with the address of [SetupServer]'s
 * page and keeps the server running while open; leaving the screen stops it. The server
 * outlives this activity being recreated (a theme or size change saved from the phone), so
 * the phone's page keeps working.
 */
class PhoneSetupActivity : LauncherActivity() {

    private lateinit var binding: ActivityPhoneSetupBinding

    /** The theme and font this screen was drawn with, to tell when the phone changed them. */
    private lateinit var drawnWith: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawnWith = LauncherTheme.signature(this)
        binding = ActivityPhoneSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpWindow(binding.root)
        binding.backButton.setOnClickListener { finish() }
        Motion.pressable(binding.backButton, binding.actionButton)
        SetupServer.listener = ::onEvent
        start()
    }

    override fun onDestroy() {
        SetupServer.listener = null
        // Leaving the screen ends the session; a recreation (theme, size) keeps it.
        if (isFinishing) SetupServer.stop()
        super.onDestroy()
    }

    private fun start() {
        // A context in the interface language but not tied to this activity, for the server.
        val localized = applicationContext.createConfigurationContext(resources.configuration)
        val session = SetupServer.start(localized, palette())
        if (session == null) {
            showNoNetwork()
            return
        }
        binding.qrCode.setImageBitmap(qrBitmap(session.url))
        binding.qrCode.alpha = 1f
        binding.address.text = session.url
        binding.instructions.setText(R.string.phone_setup_instructions)
        binding.status.setText(R.string.phone_setup_waiting)
        SetupServer.lastEvent?.let(::onEvent)
        binding.actionButton.setText(R.string.phone_setup_stop)
        binding.actionButton.setOnClickListener {
            SetupServer.stop()
        }
    }

    private fun showNoNetwork() {
        binding.qrCode.setImageDrawable(null)
        binding.address.text = ""
        binding.instructions.setText(R.string.phone_setup_no_network)
        binding.status.text = ""
        binding.actionButton.setText(R.string.phone_setup_restart)
        binding.actionButton.setOnClickListener { start() }
    }

    private fun onEvent(event: SetupServer.Event) {
        when (event) {
            SetupServer.Event.Opened -> binding.status.setText(R.string.phone_setup_opened)
            is SetupServer.Event.Saved -> {
                val time = DateFormat.getTimeFormat(this).format(Date(event.atMillis))
                binding.status.text = getString(R.string.phone_setup_saved, time)
                // A new theme, font or size from the phone: redraw now. The server keeps going.
                if (needsRecreate()) recreate()
            }
            SetupServer.Event.Stopped -> {
                binding.qrCode.alpha = 0.15f
                binding.address.text = ""
                binding.status.setText(R.string.phone_setup_stopped)
                binding.actionButton.setText(R.string.phone_setup_restart)
                binding.actionButton.setOnClickListener { start() }
            }
        }
    }

    private fun needsRecreate(): Boolean =
        resources.configuration.densityDpi != UiScale.densityDpi(this, Prefs(this).uiSize) ||
            LauncherTheme.signature(this) != drawnWith

    private fun palette() = SetupPage.Palette(
        bg = hex(R.color.bg), surface = hex(R.color.surface), surface2 = hex(R.color.surface_2),
        text = hex(R.color.text_primary), text2 = hex(R.color.text_secondary), line = hex(R.color.line),
        accent = hex(R.color.accent), onAccent = hex(R.color.on_accent), warn = hex(R.color.gauge_warn),
    )

    /** CSS colour, alpha included (#RRGGBBAA). */
    private fun hex(@ColorRes id: Int): String {
        val c = ContextCompat.getColor(this, id)
        return "#%02x%02x%02x%02x".format((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF, (c ushr 24) and 0xFF)
    }

    /**
     * The code drawn at [MODULE_PX] pixels per module, plus the standard quiet zone of four:
     * drawn big rather than scaled up, which would blur the module edges cameras look for.
     */
    private fun qrBitmap(url: String): Bitmap {
        val qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM)
        val modules = qr.size + QUIET_MODULES * 2
        val size = modules * MODULE_PX
        val dark = ContextCompat.getColor(this, R.color.qr_dark)
        val light = ContextCompat.getColor(this, R.color.qr_light)
        val pixels = IntArray(size * size) { i ->
            val x = (i % size) / MODULE_PX - QUIET_MODULES
            val y = (i / size) / MODULE_PX - QUIET_MODULES
            if (x in 0 until qr.size && y in 0 until qr.size && qr.getModule(x, y)) dark else light
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val QUIET_MODULES = 4
        private const val MODULE_PX = 10
    }
}
