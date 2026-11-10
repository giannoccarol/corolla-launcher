package io.github.giannoccarol.corollalauncher.home

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.apps.AppActions
import io.github.giannoccarol.corollalauncher.apps.AppDrawerActivity
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.databinding.ActivityHomeBinding
import io.github.giannoccarol.corollalauncher.media.MediaKeys
import io.github.giannoccarol.corollalauncher.media.MediaSessionMonitor
import io.github.giannoccarol.corollalauncher.nav.NavigationShortcuts
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.settings.SettingsActivity
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.SystemBars
import io.github.giannoccarol.corollalauncher.ui.TextPrompt
import java.util.Locale

/**
 * The home screen: clock and navigation shortcuts on the left, the media player on the right,
 * and a dock with the most used apps and the volume at the bottom.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: Prefs
    private lateinit var audio: AudioManager
    private lateinit var media: MediaSessionMonitor

    private val handler = Handler(Looper.getMainLooper())
    private val appsListener: () -> Unit = { renderDock() }

    /** Packages currently shown as source chips, to avoid rebuilding them on every update. */
    private var shownSources: List<String> = emptyList()

    /** App keys currently in the dock, for the same reason. */
    private var shownDock: List<String>? = null

    /** What the player last showed, to animate only real changes. */
    private var shownTrack: String? = null
    private var shownPlaying: Boolean? = null

    private val progressTicker = object : Runnable {
        override fun run() {
            renderProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_TICK -> ThemeManager.apply(context)
                ACTION_VOLUME_CHANGED -> renderVolume()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root)

        prefs = Prefs(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        media = MediaSessionMonitor(this, ::renderMedia)

        // A launcher is the bottom of the back stack: Back must do nothing here.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setUpClock()
        setUpNavigation()
        setUpPlayer()
        setUpDock()

        AppRepository.addListener(appsListener)
        Motion.enter(binding.leftPane, binding.rightPane, binding.dock)
    }

    override fun onStart() {
        super.onStart()
        ThemeManager.apply(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(ACTION_VOLUME_CHANGED)
        }
        ContextCompat.registerReceiver(
            this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        media.start()
        handler.post(progressTicker)
        renderVolume()
        renderDock()
    }

    override fun onStop() {
        unregisterReceiver(systemReceiver)
        media.stop()
        handler.removeCallbacks(progressTicker)
        super.onStop()
    }

    override fun onDestroy() {
        AppRepository.removeListener(appsListener)
        super.onDestroy()
    }

    // region Clock

    private fun setUpClock() {
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        binding.date.format12Hour = datePattern
        binding.date.format24Hour = datePattern
    }

    // endregion

    // region Navigation shortcuts

    private fun setUpNavigation() {
        binding.whereTo.setOnClickListener {
            TextPrompt.show(
                this, R.string.nav_destination_title, R.string.nav_destination_hint,
                initial = null, positiveRes = R.string.nav_go,
            ) { query ->
                if (query.isNotEmpty() && !NavigationShortcuts.search(this, query)) {
                    toast(R.string.nav_no_app)
                }
            }
        }

        binding.navHome.setOnClickListener { goTo(prefs.homeAddress, ::editHomeAddress) }
        binding.navHome.setOnLongClickListener { editHomeAddress(); true }
        binding.navWork.setOnClickListener { goTo(prefs.workAddress, ::editWorkAddress) }
        binding.navWork.setOnLongClickListener { editWorkAddress(); true }
        Motion.pressable(binding.whereTo, binding.navHome, binding.navWork)
    }

    /** Navigates to a saved address, or asks for it the first time. */
    private fun goTo(address: String?, askForAddress: () -> Unit) {
        when {
            address.isNullOrBlank() -> askForAddress()
            !NavigationShortcuts.navigateTo(this, address) -> toast(R.string.nav_no_app)
        }
    }

    private fun editHomeAddress() {
        TextPrompt.show(
            this, R.string.settings_home_address, R.string.nav_address_hint,
            prefs.homeAddress, R.string.save,
        ) { prefs.homeAddress = it.ifEmpty { null } }
    }

    private fun editWorkAddress() {
        TextPrompt.show(
            this, R.string.settings_work_address, R.string.nav_address_hint,
            prefs.workAddress, R.string.save,
        ) { prefs.workAddress = it.ifEmpty { null } }
    }

    // endregion

    // region Media player

    private fun setUpPlayer() {
        binding.cover.clipToOutline = true

        binding.settingsButton.setOnClickListener {
            Motion.open(this, Intent(this, SettingsActivity::class.java))
        }
        binding.themeButton.setImageResource(
            if (ThemeManager.isDark(this)) R.drawable.ic_sun else R.drawable.ic_moon,
        )
        binding.themeButton.setOnClickListener { ThemeManager.toggle(this) }

        binding.prevButton.setOnClickListener {
            transport(KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.transportControls.skipToPrevious() }
        }
        binding.nextButton.setOnClickListener {
            transport(KeyEvent.KEYCODE_MEDIA_NEXT) { it.transportControls.skipToNext() }
        }
        binding.playButton.setOnClickListener {
            transport(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { controller ->
                if (controller.isPlaying()) controller.transportControls.pause()
                else controller.transportControls.play()
            }
        }
        binding.emptyAction.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                toast(R.string.settings_screen_unavailable)
            }
        }
        Motion.pressable(
            binding.settingsButton, binding.themeButton, binding.prevButton,
            binding.playButton, binding.nextButton, binding.emptyAction,
        )
    }

    /** Uses the selected session when there is one, otherwise a plain media button press. */
    private fun transport(keyCode: Int, viaSession: (MediaController) -> Unit) {
        val controller = media.current
        if (controller != null) viaSession(controller) else MediaKeys.send(audio, keyCode)
    }

    private fun renderMedia() {
        val controller = media.current
        renderSources(controller)

        if (controller == null) {
            binding.mediaContent.visibility = View.GONE
            binding.mediaEmpty.visibility = View.VISIBLE
            val hasAccess = media.hasAccess()
            binding.emptyTitle.setText(
                if (hasAccess) R.string.media_nothing_playing else R.string.media_access_needed,
            )
            binding.emptyHint.setText(
                if (hasAccess) R.string.media_nothing_playing_hint else R.string.media_access_hint,
            )
            binding.emptyAction.visibility = if (hasAccess) View.GONE else View.VISIBLE
            binding.playButton.setImageResource(R.drawable.ic_play)
            shownTrack = null
            shownPlaying = null
            return
        }

        binding.mediaEmpty.visibility = View.GONE
        binding.mediaContent.visibility = View.VISIBLE

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        binding.trackTitle.text =
            title?.takeIf { it.isNotBlank() } ?: getString(R.string.media_unknown_title)
        binding.trackArtist.text =
            artist?.takeIf { it.isNotBlank() } ?: appLabel(controller.packageName)

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) {
            ImageViewCompat.setImageTintList(binding.cover, null)
            binding.cover.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.cover.setImageBitmap(art)
        } else {
            ImageViewCompat.setImageTintList(
                binding.cover,
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)),
            )
            binding.cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.cover.setImageResource(R.drawable.ic_music)
        }

        val playing = controller.isPlaying()
        binding.playButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (shownPlaying != null && shownPlaying != playing) Motion.pop(binding.playButton)
        shownPlaying = playing

        val track = "${controller.packageName}|$title|$artist"
        if (track != shownTrack) {
            shownTrack = track
            Motion.fadeIn(binding.mediaContent)
        }
        renderProgress()
    }

    /** One chip per app with an active media session; the selected one drives the player. */
    private fun renderSources(current: MediaController?) {
        val packages = media.controllers.map { it.packageName }
        if (packages != shownSources) {
            shownSources = packages
            binding.sources.removeAllViews()
            for (packageName in packages) {
                val chip = layoutInflater
                    .inflate(R.layout.item_source_chip, binding.sources, false) as TextView
                chip.text = appLabel(packageName)
                chip.tag = packageName
                chip.setOnClickListener { media.select(packageName) }
                Motion.pressable(chip)
                binding.sources.addView(chip)
            }
        }
        for (index in 0 until binding.sources.childCount) {
            val chip = binding.sources.getChildAt(index)
            chip.isSelected = chip.tag == current?.packageName
        }
        binding.sourcesScroll.visibility = if (packages.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderProgress() {
        val controller = media.current ?: return
        val state = controller.playbackState
        val playing = controller.isPlaying()
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        if (duration <= 0L) {
            // Radio and other live streams have no length to show.
            binding.progress.progress = if (playing) PROGRESS_MAX else 0
            binding.elapsed.text = if (playing) getString(R.string.media_live) else ""
            binding.total.text = ""
            return
        }

        var position = state?.position ?: 0L
        if (state != null && playing) {
            val sinceUpdate = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (sinceUpdate * state.playbackSpeed).toLong()
        }
        position = position.coerceIn(0L, duration)
        binding.progress.progress = (position * PROGRESS_MAX / duration).toInt()
        binding.elapsed.text = DateUtils.formatElapsedTime(position / 1000)
        binding.total.text = DateUtils.formatElapsedTime(duration / 1000)
    }

    private fun MediaController.isPlaying(): Boolean =
        playbackState?.state == PlaybackState.STATE_PLAYING

    private fun appLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    // endregion

    // region Dock

    private fun setUpDock() {
        binding.allAppsButton.setOnClickListener {
            Motion.open(this, Intent(this, AppDrawerActivity::class.java))
        }
        Motion.pressable(binding.allAppsButton, binding.volumeDown, binding.volumeUp)
        binding.volumeDown.setOnClickListener { adjustVolume(AudioManager.ADJUST_LOWER) }
        binding.volumeUp.setOnClickListener { adjustVolume(AudioManager.ADJUST_RAISE) }
    }

    private fun renderDock() {
        val apps = AppRepository.dockApps(this)
        val keys = apps.map { it.key }
        if (keys == shownDock) return
        shownDock = keys

        binding.dockApps.removeAllViews()
        val icons = ArrayList<View>(apps.size)
        for (app in apps) {
            val icon = layoutInflater
                .inflate(R.layout.item_dock_app, binding.dockApps, false) as ImageView
            icon.setImageDrawable(app.icon)
            icon.contentDescription = app.label
            icon.setOnClickListener {
                if (!AppRepository.launch(this, app)) {
                    toast(getString(R.string.app_launch_failed, app.label))
                }
            }
            icon.setOnLongClickListener {
                AppActions.showMenu(it, app, ::renderDock)
                true
            }
            Motion.pressable(icon)
            binding.dockApps.addView(icon)
            icons += icon
        }
        Motion.popIn(icons)
    }

    private fun adjustVolume(direction: Int) {
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } catch (e: SecurityException) {
            // Thrown when the change would toggle Do Not Disturb; the volume simply stays put.
        }
        renderVolume()
    }

    private fun renderVolume() {
        binding.volumeValue.text = String.format(
            Locale.getDefault(), "%d", audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        )
    }

    // endregion

    private fun toast(@StringRes messageRes: Int) = toast(getString(messageRes))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Not a public constant, but stable since Android 1.5 and what volume UIs listen to. */
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val PROGRESS_MAX = 1000
        const val PROGRESS_INTERVAL_MS = 1000L
    }
}
