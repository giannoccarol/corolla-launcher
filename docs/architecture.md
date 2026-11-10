# Architecture

The launcher is deliberately small: one module, three activities, a handful of plain objects.
The target hardware is weak and the app must start instantly, so there is no framework layer
between the screens and the platform APIs.

## Screens

| Activity | Role |
| --- | --- |
| `home/HomeActivity` | The home screen. Declared with the `HOME` category, `singleTask`. |
| `apps/AppDrawerActivity` | Searchable grid of every installed app. |
| `settings/SettingsActivity` | Theme, language, addresses, system shortcuts. |

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

### Theme

- `Theme.Launcher` extends `Theme.AppCompat.DayNight.NoActionBar`. Colors are defined twice
  with identical names: `values/colors.xml` (light) and `values-night/colors.xml` (dark).
- `ThemeManager.apply()` maps the saved `ThemeMode` to an AppCompat night mode. It is called at
  process start, on every home screen start, and on each minute tick so the time-based mode
  switches on its own. AppCompat recreates the activities when the mode actually changes.
- `SystemBars.apply()` draws edge to edge, colors the system bars like the background and pads
  the root view by the insets, so the uniform theme reaches the screen edges.

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
- **Runtime permissions**: the app requests none. Media access is a special system grant.
- **A bundled font**: the system Roboto in light and medium weights keeps the APK small.
