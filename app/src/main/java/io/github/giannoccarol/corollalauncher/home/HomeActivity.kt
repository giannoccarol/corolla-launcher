package io.github.giannoccarol.corollalauncher.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothAdapter
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
import android.location.LocationManager
import android.provider.MediaStore
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.apps.AppActions
import io.github.giannoccarol.corollalauncher.apps.AppDrawerActivity
import io.github.giannoccarol.corollalauncher.apps.AppInfo
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.assistant.AppMatcher
import io.github.giannoccarol.corollalauncher.assistant.AssistantAction
import io.github.giannoccarol.corollalauncher.assistant.AssistantHost
import io.github.giannoccarol.corollalauncher.assistant.AssistantPanel
import io.github.giannoccarol.corollalauncher.assistant.CarContext
import io.github.giannoccarol.corollalauncher.assistant.MediaCommand
import io.github.giannoccarol.corollalauncher.assistant.Speaker
import io.github.giannoccarol.corollalauncher.databinding.ActivityHomeBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemAppCompactBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemNavPlaceBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemQuickTileBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemRecentDestinationBinding
import io.github.giannoccarol.corollalauncher.databinding.ItemTripStatBinding
import io.github.giannoccarol.corollalauncher.keys.NavKeyService
import io.github.giannoccarol.corollalauncher.media.MediaKeys
import io.github.giannoccarol.corollalauncher.media.MediaSessionMonitor
import io.github.giannoccarol.corollalauncher.nav.NavigationShortcuts
import io.github.giannoccarol.corollalauncher.panels.AudioPanel
import io.github.giannoccarol.corollalauncher.panels.BluetoothPanel
import io.github.giannoccarol.corollalauncher.panels.DisplayPanel
import io.github.giannoccarol.corollalauncher.panels.PermissionAsker
import io.github.giannoccarol.corollalauncher.panels.Sheet
import io.github.giannoccarol.corollalauncher.panels.WifiPanel
import io.github.giannoccarol.corollalauncher.nav.RecentDestinations
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.settings.SettingsActivity
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.trip.Compass
import io.github.giannoccarol.corollalauncher.trip.TripService
import io.github.giannoccarol.corollalauncher.trip.TripTracker
import io.github.giannoccarol.corollalauncher.ui.LauncherActivity
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.TextPrompt
import java.util.Locale

/**
 * The home dashboard. Top bar with the driver's name, tabs and status; three columns of cards
 * (navigation and speed, media and most used apps, the car and quick settings); and a bottom
 * bar with the system keys, the docked apps and the volume.
 */
class HomeActivity : LauncherActivity(), PermissionAsker, AssistantHost {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: Prefs
    private lateinit var audio: AudioManager
    private lateinit var media: MediaSessionMonitor
    private lateinit var toggles: QuickToggles
    private lateinit var speaker: Speaker

    private val handler = Handler(Looper.getMainLooper())
    private val appsListener: () -> Unit = { renderApps() }
    private val tripListener: () -> Unit = { renderTrip() }

    /** What is currently shown, so lists are rebuilt and animated only on a real change. */
    private var shownSources: List<String> = emptyList()
    private var shownApps: List<String>? = null
    private var shownTrack: String? = null
    private var shownPlaying: Boolean? = null

    /** The panel currently open, refreshed when a permission or a system screen comes back. */
    private var panel: Sheet? = null

    private val locationRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            TripService.start(this)
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION,
            )
        ) {
            // "Don't ask again" was chosen: only the app settings can change it now.
            toast(R.string.location_denied)
            openAppDetails()
        }
        renderTrip()
        refreshPanel()
    }

    private val microphoneRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPanel() }

    private val nearbyRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPanel() }

    private val progressTicker = object : Runnable {
        override fun run() {
            renderProgress()
            renderTrip()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_TICK -> ThemeManager.apply(context)
                ACTION_VOLUME_CHANGED -> renderVolume()
                else -> renderQuickSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpWindow(binding.root)

        prefs = Prefs(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        media = MediaSessionMonitor(this, ::renderMedia)
        toggles = QuickToggles(this)
        speaker = Speaker(this)

        // A launcher is the bottom of the back stack: Back must do nothing here.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setUpTopBar()
        setUpNavigation()
        setUpPlayer()
        setUpCar()
        setUpQuickSettings()
        setUpDock()

        AppRepository.addListener(appsListener)
        TripTracker.addListener(tripListener)
        Motion.enter(binding.navCard, binding.centerColumn, binding.rightColumn, binding.dock)
        if (savedInstanceState == null) handleVoiceIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceIntent(intent)
    }

    /**
     * The voice button on the wheel or panel usually sends one of these, or a key event; the
     * system turns the VOICE_ASSIST key into WEB_SEARCH when no voice app is installed.
     */
    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.action in VOICE_ACTIONS) openAssistant()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_SEARCH) {
            openAssistant()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        ThemeManager.apply(this)
        val local = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(ACTION_VOLUME_CHANGED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, local, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Exported: the Bluetooth broadcast comes from the Bluetooth process. Both actions are
        // protected, so no other app can send them.
        val radios = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, radiosReceiver, radios, ContextCompat.RECEIVER_EXPORTED)

        media.start()
        TripService.start(this)
        handler.post(progressTicker)
        renderDriver()
        renderPlaces()
        renderVolume()
        renderApps()
        renderQuickSettings()
        renderTrip()
    }

    override fun onResume() {
        super.onResume()
        refreshPanel()
    }

    override fun onStop() {
        unregisterReceiver(systemReceiver)
        unregisterReceiver(radiosReceiver)
        media.stop()
        handler.removeCallbacks(progressTicker)
        super.onStop()
    }

    override fun onDestroy() {
        panel?.dismiss()
        speaker.shutdown()
        AppRepository.removeListener(appsListener)
        TripTracker.removeListener(tripListener)
        super.onDestroy()
    }

    /** A second receiver so the two registrations can use different export flags. */
    private val radiosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = renderQuickSettings()
    }

    // region Top bar

    private fun setUpTopBar() {
        binding.driver.setOnClickListener { editDriverName() }
        binding.tabHome.setOnClickListener { Motion.pop(binding.cards) }
        binding.tabNavigation.setOnClickListener {
            if (!NavigationShortcuts.openMap(this)) toast(R.string.nav_no_app)
        }
        binding.tabApps.setOnClickListener { openDrawer() }
        binding.assistantButton.setOnClickListener { openAssistant() }
        Motion.pressable(
            binding.driver, binding.tabHome, binding.tabNavigation, binding.tabApps,
            binding.assistantButton,
        )
    }

    private fun renderDriver() {
        val name = prefs.driverName
        if (name == null) {
            binding.driverName.setText(R.string.driver_add_name)
            binding.driverName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.driverInitial.text = ""
        } else {
            binding.driverName.text = name
            binding.driverName.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.driverInitial.text =
                name.first().toString().uppercase(Locale.getDefault())
        }
        binding.driver.contentDescription =
            getString(R.string.settings_driver_name) + ": " + (name ?: getString(R.string.settings_not_set))
    }

    private fun editDriverName() {
        TextPrompt.show(
            this, R.string.settings_driver_name, R.string.settings_driver_name_hint,
            prefs.driverName, R.string.save,
        ) {
            prefs.driverName = it.ifEmpty { null }
            renderDriver()
        }
    }

    // endregion

    // region Navigation card

    private fun setUpNavigation() {
        binding.whereTo.setOnClickListener {
            TextPrompt.show(
                this, R.string.nav_destination_title, R.string.nav_destination_hint,
                initial = null, positiveRes = R.string.nav_go,
            ) { query ->
                if (query.isEmpty()) return@show
                prefs.recentDestinations = RecentDestinations.push(prefs.recentDestinations, query)
                renderPlaces()
                if (!NavigationShortcuts.search(this, query)) toast(R.string.nav_no_app)
            }
        }

        setUpPlace(binding.navHome, R.drawable.ic_home, R.string.home_home,
            { prefs.homeAddress }, ::editHomeAddress)
        setUpPlace(binding.navWork, R.drawable.ic_work, R.string.home_work,
            { prefs.workAddress }, ::editWorkAddress)

        binding.speedPanel.setOnClickListener {
            if (!TripTracker.hasPermission(this)) askForLocation()
        }
        Motion.pressable(binding.whereTo, binding.navHome.root, binding.navWork.root, binding.speedPanel)
    }

    private fun setUpPlace(
        place: ItemNavPlaceBinding,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        address: () -> String?,
        edit: () -> Unit,
    ) {
        place.icon.setImageResource(icon)
        place.title.setText(title)
        place.root.setOnClickListener { goTo(address(), edit) }
        place.root.setOnLongClickListener { edit(); true }
    }

    private fun renderPlaces() {
        val tapToSet = getString(R.string.nav_tap_to_set)
        binding.navHome.subtitle.text = prefs.homeAddress ?: tapToSet
        binding.navWork.subtitle.text = prefs.workAddress ?: tapToSet

        binding.recentList.removeAllViews()
        val recents = prefs.recentDestinations.take(RECENTS_SHOWN)
        for (destination in recents) {
            val row = ItemRecentDestinationBinding.inflate(layoutInflater, binding.recentList, false)
            row.label.text = destination
            row.root.setOnClickListener {
                prefs.recentDestinations = RecentDestinations.push(prefs.recentDestinations, destination)
                if (!NavigationShortcuts.navigateTo(this, destination)) toast(R.string.nav_no_app)
                renderPlaces()
            }
            Motion.pressable(row.root)
            binding.recentList.addView(row.root)
        }
        binding.recentEmpty.visibility = if (recents.isEmpty()) View.VISIBLE else View.GONE
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
        ) {
            prefs.homeAddress = it.ifEmpty { null }
            renderPlaces()
        }
    }

    private fun editWorkAddress() {
        TextPrompt.show(
            this, R.string.settings_work_address, R.string.nav_address_hint,
            prefs.workAddress, R.string.save,
        ) {
            prefs.workAddress = it.ifEmpty { null }
            renderPlaces()
        }
    }

    // endregion

    // region Speed and trip

    private fun setUpCar() {
        labelStat(binding.statDistance, R.string.trip_distance)
        labelStat(binding.statDuration, R.string.trip_duration)
        labelStat(binding.statAverage, R.string.trip_average)
        binding.carCard.setOnClickListener {
            if (!TripTracker.hasPermission(this)) askForLocation()
        }
    }

    // region Assistant

    private fun openAssistant() {
        if (panel is AssistantPanel && panel?.isShowing == true) return
        openPanel(AssistantPanel(this, this, speaker))
    }

    override fun askForMicrophone() {
        microphoneRequest.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun openSettings() {
        Motion.open(this, Intent(this, SettingsActivity::class.java))
    }

    override fun carContext(): CarContext {
        val now = java.util.Date()
        val time = android.text.format.DateFormat.getTimeFormat(this).format(now)
        val day = android.text.format.DateFormat.format("EEEE d MMMM yyyy", now)
        val metadata = media.current?.metadata
        val playing = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.let { title ->
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            listOfNotNull(title, artist, media.current?.packageName?.let(::appLabel)).joinToString(" - ")
        }
        return CarContext(
            localTime = "$day, $time",
            speedKmh = if (TripTracker.hasPermission(this)) TripTracker.speedKmh() else null,
            tripKm = if (TripTracker.hasTrip()) TripTracker.distanceKm() else null,
            nowPlaying = playing,
            homeSaved = prefs.homeAddress != null,
            workSaved = prefs.workAddress != null,
            darkTheme = ThemeManager.isDark(this),
            appNames = AppRepository.apps.map { it.label }.distinct(),
            position = currentPosition(),
        )
    }

    /** The last GPS fix, or the system's last known position, when location is allowed. */
    @SuppressLint("MissingPermission") // Checked first.
    private fun currentPosition(): Pair<Double, Double>? {
        if (!TripTracker.hasPermission(this)) return null
        TripTracker.lastPosition?.let { return it }
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: IllegalArgumentException) {
                    null // This unit has no such provider.
                } catch (e: SecurityException) {
                    null
                }
            }
            ?.let { it.latitude to it.longitude }
    }

    /** "Play X": the standard media search intent, which Spotify and most music apps handle. */
    private fun playMusic(query: String, appName: String?): String? {
        val app = appName?.let { name ->
            AppRepository.apps.let { apps -> AppMatcher.find(apps.map { it.label }, name)?.let(apps::get) }
        }
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app?.let { intent.setPackage(it.component.packageName) }
        return try {
            startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            if (app != null) {
                // That app does not take searches: let any app that does.
                try {
                    startActivity(intent.setPackage(null))
                    null
                } catch (e: ActivityNotFoundException) {
                    getString(R.string.assistant_music_failed)
                }
            } else {
                getString(R.string.assistant_music_failed)
            }
        }
    }

    override fun perform(action: AssistantAction): String? = when (action) {
        is AssistantAction.Navigate -> {
            prefs.recentDestinations = RecentDestinations.push(prefs.recentDestinations, action.destination)
            renderPlaces()
            if (NavigationShortcuts.navigateTo(this, action.destination)) null else getString(R.string.nav_no_app)
        }
        AssistantAction.NavigateHome -> navigateSaved(prefs.homeAddress, R.string.assistant_no_home)
        AssistantAction.NavigateWork -> navigateSaved(prefs.workAddress, R.string.assistant_no_work)
        is AssistantAction.Media -> {
            when (action.command) {
                MediaCommand.PLAY -> transport(KeyEvent.KEYCODE_MEDIA_PLAY) { it.transportControls.play() }
                MediaCommand.PAUSE -> transport(KeyEvent.KEYCODE_MEDIA_PAUSE) { it.transportControls.pause() }
                MediaCommand.NEXT -> transport(KeyEvent.KEYCODE_MEDIA_NEXT) { it.transportControls.skipToNext() }
                MediaCommand.PREVIOUS ->
                    transport(KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.transportControls.skipToPrevious() }
            }
            null
        }
        is AssistantAction.PlayMusic -> playMusic(action.query, action.app)
        is AssistantAction.OpenApp -> {
            val apps = AppRepository.apps
            val index = AppMatcher.find(apps.map { it.label }, action.name)
            when {
                index == null -> getString(R.string.assistant_app_not_found, action.name)
                AppRepository.launch(this, apps[index]) -> null
                else -> getString(R.string.app_launch_failed, apps[index].label)
            }
        }
        is AssistantAction.Volume -> {
            if (action.percent != null) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * action.percent + 50) / 100, 0)
                } catch (e: SecurityException) {
                    // Do Not Disturb: leave the volume as it is.
                }
                renderVolume()
            } else {
                adjustVolume(if (action.step > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
            }
            null
        }
        is AssistantAction.Theme -> {
            prefs.themeMode = if (action.dark) ThemeMode.DARK else ThemeMode.LIGHT
            ThemeManager.apply(this)
            null
        }
    }

    private fun navigateSaved(address: String?, @StringRes missing: Int): String? = when {
        address.isNullOrBlank() -> getString(missing)
        NavigationShortcuts.navigateTo(this, address) -> null
        else -> getString(R.string.nav_no_app)
    }

    // endregion

    override fun askForNearbyDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            nearbyRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun openPanel(sheet: Sheet) {
        panel?.dismiss()
        panel = sheet
        sheet.show()
    }

    private fun refreshPanel() {
        panel?.takeIf { it.isShowing }?.render()
    }

    private fun labelStat(stat: ItemTripStatBinding, @StringRes label: Int) {
        stat.label.setText(label)
    }

    /** Asked only when the driver taps the speed or car card or a panel row, never on its own. */
    override fun askForLocation() {
        locationRequest.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    private fun renderTrip() {
        val allowed = TripTracker.hasPermission(this)
        val dash = getString(R.string.value_missing)

        val speed = if (allowed) TripTracker.speedKmh() else null
        binding.speedValue.update(speed?.let { formatInt(it) } ?: dash)
        val bearing = TripTracker.bearing
        when {
            !allowed -> {
                binding.headingValue.update(getString(R.string.speed_tap_to_enable))
                binding.headingLabel.visibility = View.GONE
            }
            else -> {
                binding.headingValue.update(
                    bearing?.let { getString(COMPASS[Compass.pointIndex(it)]) } ?: dash,
                )
                binding.headingLabel.visibility = View.VISIBLE
            }
        }

        binding.tripState.update(
            getString(
                when {
                    !allowed -> R.string.trip_location_off
                    !TripTracker.gpsEnabled(this) -> R.string.trip_gps_off
                    !TripTracker.hasFix -> R.string.trip_waiting_gps
                    TripTracker.isMoving() -> R.string.trip_moving
                    else -> R.string.trip_stopped
                },
            ),
        )

        val hasTrip = allowed && TripTracker.hasTrip()
        binding.statDistance.value.update(if (hasTrip) {
            getString(R.string.trip_km_value, String.format(Locale.getDefault(), "%.1f", TripTracker.distanceKm()))
        } else {
            dash
        })
        binding.statDuration.value.update(
            if (hasTrip) formatDuration(TripTracker.durationMinutes()) else dash,
        )
        val average = if (hasTrip) TripTracker.averageKmh() else null
        binding.statAverage.value.update(average?.let { getString(R.string.trip_kmh_value, it) } ?: dash)
    }

    private fun formatDuration(minutes: Long): String =
        if (minutes < 60) getString(R.string.trip_minutes_value, minutes.toInt())
        else getString(R.string.trip_hours_minutes_value, (minutes / 60).toInt(), (minutes % 60).toInt())

    /**
     * Sets the text only when it changed. The home screen refreshes every second; rewriting
     * identical text would still trigger layout passes and accessibility events.
     */
    private fun TextView.update(value: CharSequence) {
        if (text.toString() != value.toString()) text = value
    }

    private fun formatInt(value: Int): String = String.format(Locale.getDefault(), "%d", value)

    // endregion

    // region Media player

    private fun setUpPlayer() {
        binding.cover.clipToOutline = true
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
            openSystem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        Motion.pressable(binding.prevButton, binding.playButton, binding.nextButton, binding.emptyAction)
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
            binding.elapsed.update(if (playing) getString(R.string.media_live) else "")
            binding.total.update("")
            return
        }

        var position = state?.position ?: 0L
        if (state != null && playing) {
            val sinceUpdate = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (sinceUpdate * state.playbackSpeed).toLong()
        }
        position = position.coerceIn(0L, duration)
        binding.progress.progress = (position * PROGRESS_MAX / duration).toInt()
        binding.elapsed.update(DateUtils.formatElapsedTime(position / 1000))
        binding.total.update(DateUtils.formatElapsedTime(duration / 1000))
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

    // region Quick settings

    private fun setUpQuickSettings() {
        tile(binding.qsWifi, R.drawable.ic_wifi, R.string.qs_wifi) { openPanel(WifiPanel(this, this)) }
        tile(binding.qsBluetooth, R.drawable.ic_bluetooth, R.string.qs_bluetooth) {
            openPanel(BluetoothPanel(this, this))
        }
        tile(binding.qsDisplay, R.drawable.ic_brightness, R.string.qs_display) {
            openPanel(DisplayPanel(this))
        }
        tile(binding.qsAudio, R.drawable.ic_equalizer, R.string.qs_audio) { openPanel(AudioPanel(this)) }
        tile(binding.qsMore, R.drawable.ic_settings, R.string.qs_more) {
            Motion.open(this, Intent(this, SettingsActivity::class.java))
        }
        val dark = ThemeManager.isDark(this)
        tile(
            binding.qsTheme, if (dark) R.drawable.ic_moon else R.drawable.ic_sun,
            if (dark) R.string.theme_dark else R.string.theme_light,
        ) { ThemeManager.toggle(this) }
    }

    private fun tile(
        tile: ItemQuickTileBinding,
        @DrawableRes icon: Int,
        @StringRes label: Int,
        onClick: () -> Unit,
    ) {
        tile.icon.setImageResource(icon)
        tile.label.setText(label)
        tile.root.setOnClickListener { onClick() }
        Motion.pressable(tile.root)
    }

    private fun renderQuickSettings() {
        setTileOn(binding.qsWifi, toggles.wifiEnabled())
        setTileOn(binding.qsBluetooth, toggles.bluetoothEnabled())
    }

    private fun setTileOn(tile: ItemQuickTileBinding, on: Boolean) {
        tile.root.setBackgroundResource(if (on) R.drawable.bg_tile_on else R.drawable.bg_tile)
        val color = ContextCompat.getColor(this, if (on) R.color.tile_on_text else R.color.text_primary)
        tile.label.setTextColor(color)
        ImageViewCompat.setImageTintList(tile.icon, ColorStateList.valueOf(color))
        tile.root.isSelected = on
    }

    // endregion

    // region Apps and dock

    private fun setUpDock() {
        binding.allAppsButton.setOnClickListener { openDrawer() }
        binding.allAppsLink.setOnClickListener { openDrawer() }
        // On the home screen itself Back and Home have nothing to do; they are here so the
        // system keys keep their usual place while the system bar is hidden.
        binding.keyBack.setOnClickListener {
            NavKeyService.perform(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        binding.keyHome.setOnClickListener {
            NavKeyService.perform(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        binding.keyRecents.setOnClickListener {
            if (!NavKeyService.perform(AccessibilityService.GLOBAL_ACTION_RECENTS)) {
                explainNavKeys()
            }
        }
        binding.volumeDown.setOnClickListener { adjustVolume(AudioManager.ADJUST_LOWER) }
        binding.volumeUp.setOnClickListener { adjustVolume(AudioManager.ADJUST_RAISE) }
        Motion.pressable(
            binding.keyBack, binding.keyHome, binding.keyRecents, binding.allAppsButton,
            binding.allAppsLink, binding.volumeDown, binding.volumeUp,
        )
    }

    private fun openDrawer() {
        Motion.open(this, Intent(this, AppDrawerActivity::class.java))
    }

    /** Recents needs the accessibility service; say so and offer the way to turn it on. */
    private fun explainNavKeys() {
        AlertDialog.Builder(this)
            .setTitle(R.string.nav_keys_enable_title)
            .setMessage(getString(R.string.nav_keys_enable_message, getString(R.string.app_name)))
            .setPositiveButton(R.string.nav_keys_open_settings) { _, _ ->
                openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderApps() {
        val dock = AppRepository.dockApps(this)
        val mostUsed = AppRepository.mostUsedApps(this, dock)
        val keys = (dock + mostUsed).map { it.key }
        if (keys == shownApps) return
        shownApps = keys

        binding.dockLeft.removeAllViews()
        binding.dockRight.removeAllViews()
        val icons = ArrayList<View>()
        dock.forEachIndexed { index, app ->
            val target = if (index < dock.size / 2 + dock.size % 2) binding.dockLeft else binding.dockRight
            val icon = layoutInflater.inflate(R.layout.item_dock_app, target, false) as ImageView
            icon.setImageDrawable(app.icon)
            icon.contentDescription = app.label
            bindApp(icon, app)
            target.addView(icon)
            icons += icon
        }

        binding.mostUsed.removeAllViews()
        for (app in mostUsed) {
            val item = ItemAppCompactBinding.inflate(layoutInflater, binding.mostUsed, false)
            item.icon.setImageDrawable(app.icon)
            item.label.text = app.label
            bindApp(item.root, app)
            binding.mostUsed.addView(item.root)
            icons += item.root
        }
        Motion.popIn(icons)
    }

    private fun bindApp(view: View, app: AppInfo) {
        view.setOnClickListener {
            if (!AppRepository.launch(this, app)) toast(getString(R.string.app_launch_failed, app.label))
        }
        view.setOnLongClickListener {
            AppActions.showMenu(it, app) {
                shownApps = null
                renderApps()
            }
            true
        }
        Motion.pressable(view)
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
        binding.volumeValue.text = formatInt(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    // endregion

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.settings_screen_unavailable)
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null)),
            )
        } catch (e: ActivityNotFoundException) {
            toast(R.string.settings_screen_unavailable)
        }
    }

    private fun toast(@StringRes messageRes: Int) = toast(getString(messageRes))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Not a public constant, but stable since Android 1.5 and what volume UIs listen to. */
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val PROGRESS_MAX = 1000
        const val TICK_MS = 1000L
        const val RECENTS_SHOWN = 2

        /** Not in the public SDK, but what the system sends for the VOICE_ASSIST key. */
        const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"

        val VOICE_ACTIONS = setOf(
            Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST, ACTION_VOICE_ASSIST,
            RecognizerIntent.ACTION_WEB_SEARCH, RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE,
        )

        val COMPASS = intArrayOf(
            R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
            R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw,
        )
    }
}
