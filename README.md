# Corolla Launcher

A clean, car-first Android home screen for an aftermarket head unit. Built for a Podofo
Android unit in a 2005 Toyota Corolla E120, but nothing in the code is specific to that car:
it should run on any landscape Android head unit.

| Dark (default) | Light |
| --- | --- |
| ![Home screen, dark theme](docs/screenshots/home-dark.png) | ![Home screen, light theme](docs/screenshots/home-light.png) |

> **Status: 0.1.0, tested on an emulator only.** The app has not run on the real head unit
> yet. See [Known limits](#known-limits) and [docs/head-unit.md](docs/head-unit.md).

## What it does

- **Home screen** with a large clock, navigation shortcuts, a media player and a dock.
- **Full screen, with its own status bar.** The system bars are hidden while the launcher is in
  front. A slim bar in the app's style shows time, date and whether GPS, Bluetooth and the
  network are on.
- **System keys in the dock**: Back, Home and Recent apps sit bottom left, where the hidden
  system bar would have had them.
- **Media player** that follows whatever is playing. Every app with an active media session
  (Spotify, YouTube Music, a podcast app...) appears as a chip; tap one to switch source.
  Shows title, artist, cover and progress; play/pause/next/previous always work.
- **Dock** with the real icons of your most used apps. It fills itself from launch counts,
  and you can pin apps with a long press. Volume controls sit on the right.
- **All apps** grid with search. Long press an app to pin it or open its system info.
- **Navigation shortcuts**: "Where to?" searches in your maps app; Home and Work start
  navigation to saved addresses (long press to change them).
- **Light and dark themes**, always uniform: all black or all white, never mixed. Dark by
  default, with manual, follow-system and time-based modes.
- **Five languages**: English, Italian, German, Spanish, French. Switch in Settings, on any
  Android version.
- **Light animations, no touch sounds.** Buttons react to the press, screens and dock icons
  ease in, the player fades between tracks. Only GPU-composited properties are animated, and
  *Settings → Animations* turns all of it off on a unit that cannot keep up. The app never
  plays a click sound.

## Requirements

- Android 7.0 (API 24) or newer on the device.
- To build: JDK 17 or 21, and the Android SDK with platform `android-37.2`.
  The Gradle wrapper downloads Gradle 9.8.1 itself.

## Build

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. If your default `java` is newer
than 21, point `JAVA_HOME` at a JDK 17 or 21 first.

Other useful commands:

```bash
./gradlew :app:testDebugUnitTest      # unit tests
python3 scripts/check_translations.py # every language has every string
./gradlew :app:assembleRelease        # minified build, debug-signed unless keystore.properties exists
```

## Try it on an emulator

`scripts/emulator.sh` creates an emulator shaped like the head unit (1024x600, 160 dpi):

```bash
scripts/emulator.sh create   # once; needs the "system-images;android-30;default;x86_64" image
scripts/emulator.sh window   # boot with a visible window (use "start" for headless)
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
```

## Install on the head unit

1. Enable developer options and USB or Wi-Fi debugging on the unit, then connect with `adb`.
2. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Press Home and choose **Corolla Launcher**, or open *Settings → Default launcher* in the app.
   Some units hide this choice; see [docs/head-unit.md](docs/head-unit.md) for workarounds.
4. Allow media access so the player can see what is playing. Either tap **Allow access** on the
   home screen, or grant it over adb:

   ```bash
   adb shell cmd notification allow_listener \
     io.github.giannoccarol.corollalauncher.debug/io.github.giannoccarol.corollalauncher.media.MediaListenerService
   ```

   Drop `.debug` from the package name for release builds.
5. To make the **Recent apps** key work, enable *Corolla Launcher* under the system
   Accessibility settings (the app explains this the first time you tap the key), or over adb:

   ```bash
   adb shell settings put secure enabled_accessibility_services \
     io.github.giannoccarol.corollalauncher.debug/io.github.giannoccarol.corollalauncher.keys.NavKeyService
   adb shell settings put secure accessibility_enabled 1
   ```

   The adb command replaces any accessibility service already enabled on the device. The
   service only sends Back, Home and Recents: it subscribes to no events and cannot read
   screen content.

## Using it

| Action | How |
| --- | --- |
| Open an app | Tap its icon in the dock or in **All apps** (grid button, left of the icons) |
| See recent apps | Square key, bottom left (needs the accessibility service, see above) |
| Show the system bars | Swipe in from the top or bottom edge; they hide again by themselves |
| Pin or unpin a dock app | Long press the icon |
| Switch media source | Tap the app chip at the top of the player |
| Set Home / Work address | Long press the button, or *Settings → Navigation* |
| Switch theme | Sun/moon button (top right), or *Settings → Theme* for all modes |
| Change language | *Settings → Language* |
| Turn animations off | *Settings → Animations* |

## Known limits

- **Not yet run on the real head unit.** Everything below was verified on the emulator only.
- **The media player with a live session is untested.** The empty and "allow access" states
  were checked; the playing state could not be, because the emulator image has no music app.
- **Stock radio and Bluetooth apps may not show up in the player.** Many head units play
  radio and Bluetooth audio without a media session, which makes them invisible to any
  third-party app. The transport buttons still send media key presses.
- **Volume buttons may do nothing on some units**, where the amplifier is driven by the
  unit's own controller instead of Android's volume.
- **Notification access can be unavailable** on low-RAM Android 8 builds. The player then
  stays in its fallback mode.
- **Back and Home do nothing on the home screen itself.** There is nowhere to go back to and
  you are already home. They are there so the keys keep their place; Recent apps is the one
  that acts.
- **The Bluetooth icon means "Bluetooth is on"**, not "a phone is connected". Showing the
  connection would need a runtime permission the app currently avoids.
- **Hiding the system bar is untested on the unit.** Some firmwares draw their bar as an
  overlay that an app cannot hide.
- **Animation smoothness is unmeasured on real hardware.** They are built to be cheap, but if
  they stutter on the unit, turn them off in Settings.
- **The unit may still beep on touch.** The app's own click sounds are off, but many head
  units have a separate system-wide touch beep in their own settings, which no app can mute.

## Project layout

```
app/src/main/java/.../corollalauncher/
  LauncherApp.kt          process setup: theme, package change listener
  home/                   the home screen
  apps/                   app list, dock selection, icons, app drawer
  media/                  media session tracking and media key fallback
  status/                 the launcher's own status bar
  keys/                   accessibility service behind the dock's system keys
  nav/                    navigation intents
  settings/               preferences, languages, settings screen
  theme/                  light/dark handling
  ui/                     base activity, full-screen handling, text dialog, motion
app/src/main/res/
  values/ values-night/   light and dark colors (same names in both)
  values-xx/              translations
docs/                     architecture, design tokens, head unit notes
scripts/                  translation check, emulator helper
.claude/                  skills and agents for AI coding assistants
```

More detail in [docs/architecture.md](docs/architecture.md) and
[docs/design.md](docs/design.md).

## Working on it with an AI assistant

[AGENTS.md](AGENTS.md) holds the rules an assistant must follow in this repo (it is also
loaded by Claude Code through `CLAUDE.md`). `.claude/skills/` has step-by-step procedures for
building and installing, adding a language, and probing the head unit; `.claude/agents/` has
two reviewers for head unit constraints and translations.

## Roadmap

- Configurable home: choose and arrange widgets (speedometer from GPS, weather, radio).
- Accent color and night hours in Settings.
- Theme switching from the headlight signal, if the unit exposes it.
- Launching the stock radio, Bluetooth and reverse camera apps by name once their package
  names are known.

## Credits

- [AndroidX](https://developer.android.com/jetpack/androidx) (AppCompat, Core KTX,
  RecyclerView) by Google and the Android Open Source Project, Apache License 2.0.
- Build tooling: [Gradle](https://gradle.org) and the Android Gradle Plugin.
- Icons in the app are drawn for this project. App icons shown at runtime belong to their
  respective apps and are read from the device, not bundled.
- The layout takes general inspiration from modern in-car interfaces (large type, bottom
  dock, uniform light and dark themes). No third-party code, artwork or fonts were copied.

## License

Private project, all rights reserved. No license is granted.
