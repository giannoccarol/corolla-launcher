# Architecture

The launcher is deliberately small: one module, three activities, a handful of plain objects.
The target hardware is weak and the app must start instantly, so there is no framework layer
between the screens and the platform APIs.

## Screens

| Activity | Role |
| --- | --- |
| `home/HomeActivity` | The home dashboard. Declared with the `HOME` category, `singleTask`. |
| `apps/AppDrawerActivity` | Searchable grid of every installed app. |
| `settings/SettingsActivity` | Theme, language, addresses, system shortcuts. |

All three extend `ui/LauncherActivity`, which makes the window full screen (system bars
hidden, re-hidden whenever the window regains focus) and starts and stops the status bar.

`HomeActivity` is the root of the task. Pressing Home from the drawer or settings brings it
back and clears whatever was above it; pressing Back on it does nothing, on purpose.

## Data flow

```
PackageManager ──▶ AppRepository ──▶ dock (HomeActivity)
                        │       └──▶ grid (AppDrawerActivity)
                        ▼
              Prefs (pins, launch counts) ──▶ DockSelector

MediaSessionManager ──▶ MediaSessionMonitor ──▶ player card (HomeActivity)
AudioManager ─────────────────────────────────▶ volume, media key fallback
```

### Apps

- `AppRepository` is a process-wide object. It loads launchable activities on a background
  thread, shapes their icons once (`IconShaper`), and notifies listeners on the main thread.
- `LauncherApp` registers for package added/removed/changed broadcasts and triggers a reload,
  so the list stays correct even while the home screen is not visible.
- `DockSelector` is pure logic and unit tested. Order: pinned apps, then most launched, then
  apps whose package looks like a car essential (maps, music, radio, phone), then the rest.
- Launch counts are the launcher's own (`Prefs.incrementLaunch`). They only count launches made
  from this launcher, which avoids the `PACKAGE_USAGE_STATS` permission.

### Media

- `MediaListenerService` is an empty `NotificationListenerService`. Android only lets an app
  enumerate other apps' media sessions if it owns an enabled notification listener; the
  service exists to hold that grant and does nothing else.
- `MediaSessionMonitor` keeps one `MediaController` per app with an active session, registers a
  callback on each, and exposes `current`: the user's pick, else whichever is playing, else the
  first. `HomeActivity` re-renders on every change and ticks the progress bar once a second
  while visible.
- When there is no session (no access granted, or the playing app has none), the transport
  buttons fall back to `MediaKeys`, which dispatches media button events through `AudioManager`.

### Speed and trip computer

- `trip/TripTracker` is a process-wide object that requests GPS updates once a second and
  feeds them to `trip/TripStats`, a pure class (unit tested) that accumulates distance, moving
  time and average speed, filters bad fixes (accuracy over 50 m, jumps faster than 70 m/s) and
  starts a new trip after 20 minutes standing still. Times come from
  `Location.elapsedRealtimeNanos`, so clock changes cannot distort them.
- `trip/TripService` is a foreground service of type `location`. Android throttles location
  for background apps to a few fixes per hour, and the navigator is usually in front while
  driving; the service keeps fixes flowing. Its notification is silent and low priority.
- Location is a runtime permission. `HomeActivity` asks for it only when the speed or car card
  is tapped, and starts the service on every `onStart` once it is granted.
- `trip/Compass` turns the bearing into one of eight compass points (unit tested).
- Trip data lives in memory only: a restart of the process starts a new trip.

### Quick settings

`home/QuickToggles` switches Wi-Fi directly on Android 9 and below (`CHANGE_WIFI_STATE`) and
Bluetooth on Android 11 and below (`BLUETOOTH_ADMIN`); on newer versions, where apps may not
do that, it opens the system panel or settings screen. Display opens the display settings and
Audio the registered equalizer, falling back to the sound settings. Tile state follows the
`WIFI_STATE_CHANGED` and Bluetooth `ACTION_STATE_CHANGED` broadcasts.

### Refresh cost

The home screen ticks once a second (media progress and trip). `TextView.update()` in
`HomeActivity` writes a text only when it actually changed; rewriting identical text would
still cause layout passes and accessibility events, and kept the screen from ever being idle.
Lists (bottom bar, most used, source chips) are rebuilt only when their content changes.

### Status bar and system keys

- `status/StatusBarController` drives the status icons through their ids: in the home top bar,
  and in `view_status_bar.xml` that the other screens include. Time
  and date are `TextClock`s. GPS and Bluetooth state come from two protected system
  broadcasts, the network from a default-network callback; all are registered in `onStart`
  and released in `onStop`.
- It needs two install-time permissions, `ACCESS_NETWORK_STATE` and (up to Android 11)
  `BLUETOOTH`. Nothing is requested at runtime.
- `keys/NavKeyService` is an accessibility service, because that is the only way Android lets
  an app trigger Back, Home and Recents. It subscribes to no event types and cannot retrieve
  window content; it only calls `performGlobalAction`. It is off until the user enables it,
  and `HomeActivity` explains how when Recents is tapped without it.

### Theme

- `Theme.Launcher` extends `Theme.AppCompat.DayNight.NoActionBar`. Colors are defined twice
  with identical names: `values/colors.xml` (light) and `values-night/colors.xml` (dark).
- `ThemeManager.apply()` maps the saved `ThemeMode` to an AppCompat night mode. It is called at
  process start, on every home screen start, and on each minute tick so the time-based mode
  switches on its own. AppCompat recreates the activities when the mode actually changes.
- `SystemBars.apply()` hides the system bars, and colors them like the background for the
  moments they do appear (an edge swipe, a dialog), so the uniform theme is never broken.

### Motion and sound

- `ui/Motion.kt` is the only place that starts animations. It animates alpha, translation and
  scale through `ViewPropertyAnimator`, which the GPU composites without re-laying-out or
  redrawing the views, and it returns early when `Motion.enabled()` is false (the Animations
  setting, or animations disabled system-wide).
- Screen transitions are window animations (`res/anim/screen_*.xml`) set with
  `overridePendingTransition`, which still works on every Android version the app supports.
- `HomeActivity` remembers what the dock and the player last showed (`shownDock`,
  `shownTrack`, `shownPlaying`) so it only rebuilds or animates on a real change, not on
  every media callback.
- Click sounds are disabled once, in the theme (`android:soundEffectsEnabled` = false).

### Language

- English strings are the base (`values/strings.xml`); each language has a `values-xx` folder.
- `Languages.set()` calls `AppCompatDelegate.setApplicationLocales`. AppCompat applies and
  persists the choice on every Android version; the `AppLocalesMetadataHolderService` entry in
  the manifest enables that persistence below Android 13.
- `res/xml/locales_config.xml` exposes the same list to the system per-app language screen on
  Android 13+.
- `scripts/check_translations.py` fails when a language is missing a string, has a stale one,
  has mismatched placeholders, or is not declared in both lists above.

## Persistence

Two `SharedPreferences` files, wrapped by `settings/Prefs`:

| File | Contents |
| --- | --- |
| `launcher` | theme mode, home and work address, pinned apps |
| `launch_counts` | launch count per app component |

## Things that are intentionally absent

- **Compose**: startup and scrolling cost too much on a Cortex-A7 class device.
- **Wallpaper and app widgets**: the home is a designed surface with a uniform background.
- **Runtime permissions on start-up**: the only one, location, is asked from a tap. Media
  access and the system keys are special grants the user turns on in system settings.
- **A map library**: see "Live map" in `docs/head-unit.md` for the options and their cost.
- **A bundled font**: the system Roboto in light and medium weights keeps the APK small.
