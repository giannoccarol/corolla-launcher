# Corolla Launcher

A clean, car-first Android home screen for an aftermarket head unit. Built for a Podofo
Android unit in a 2005 Toyota Corolla E120, but nothing in the code is specific to that car:
it should run on any landscape Android head unit.

| Dark (default) | Light |
| --- | --- |
| ![Home screen, dark theme](docs/screenshots/home-dark.png) | ![Home screen, light theme](docs/screenshots/home-light.png) |

> **Status: 0.3.0, tested on an emulator only.** The app has not run on the real head unit
> yet. See [Known limits](#known-limits) and [docs/head-unit.md](docs/head-unit.md).

## What it does

- **Dashboard home screen** made of cards, full screen, with the system bars hidden:
  - **Top bar**: Back, Home and Recent apps keys on the left, tabs for Home, Navigation (opens
    the maps app) and Apps, then GPS, Bluetooth and network status, date and time.
  - **Navigation card**: "Where to?" search, Home and Work, the last two places searched, and
    the current **speed and heading** from GPS, shown as a digital dash in the spirit of a
    late-90s roadster: a rising bar graph that turns amber past 130 km/h over big LCD digits.
  - **Media card** that follows whatever is playing. Every app with an active media session
    (Spotify, YouTube Music, a podcast app...) appears as a chip; tap one to switch source.
    Title, artist, cover and progress; play/pause/next/previous always work.
  - **Most used**: the four apps you launch most.
  - **Car card**: a photo of the Corolla sitting on a soft ground shadow, and a **trip computer** (distance, duration, average
    speed) measured from GPS. A trip ends after 20 minutes standing still.
  - **Quick settings**: theme, plus Wi-Fi, Bluetooth, display and audio tiles that open the
    launcher's own panels instead of system settings: Wi-Fi switch and nearby networks,
    Bluetooth switch and paired phones, brightness and theme, and volume per stream. Sliders are
    thick and flanked by large minus and plus buttons for use while driving.
  - **Bottom bar**: your name (configurable) on the left, four docked apps around the All apps
    button, always centred, and volume on the right. The head unit's **Android Auto / CarPlay app** (Phone Link,
    ZLink, AutoLink, Carlinkit...) is found automatically and always comes first; long press it
    to remove it. "Apri Android Auto" works with the assistant too.
- **Themed app icons** by default, like Android 13's: every app gets one glyph on a tonal tile
  in its own colour (green for Spotify, red for YouTube, grey for grey apps), pale in the light
  theme and deep in the dark one, so the bottom bar and the app grid look like one set. Well-known
  apps (Spotify, Google Maps, Waze, YouTube Music, WhatsApp, Android Auto...) show their logo;
  head unit apps get a symbol for what they do (Radio, Music, Bluetooth, Settings...), matched
  by their label in five languages; the rest show their own logo's silhouette or their initial.
  *Settings → App icons* switches to monochrome glyphs in the accent colour, to the apps'
  original icons, or to any **icon pack** installed
  from the Play Store (the appfilter.xml format Nova, ADW and Lawnchair read).
- **Voice assistant** (Assistant button in the top bar, or the steering wheel voice button):
  speak or type, hear the answer. It works with tools, so it can look things up and then act
  ("find a petrol station near here and take me there"):
  - **web search** for news, scores, opening hours and anything recent (Anthropic only);
  - **weather** now and for the next days, here or in a named town (Open-Meteo);
  - **places nearby** by category (fuel, EV charging, parking, cafés, pharmacies...) or by name,
    nearest first (OpenStreetMap);
  - **where am I**: the street address of the car;
  - **play music** by artist, song, playlist or station, optionally in a named app;
  - **actions**: navigate (to an address, a found place, home or work), media controls, open
    apps, volume, theme.

  Works with the same providers as the Monetine app, with your own API key: Anthropic (default,
  `claude-opus-5-5`), OpenAI, Google Gemini, OpenRouter, xAI, DeepSeek, Mistral, Groq and
  others. Providers without tool support fall back to the actions only.
- **All apps** grid with search. Long press an app to pin it to the bottom bar or open its
  system info.
- **Light and dark themes**, always uniform: all black or all white, never mixed. Dark by
  default, with manual, follow-system and time-based modes.
- **Five languages**: English, Italian, German, Spanish, French. Switch in Settings, on any
  Android version.
- **Finish and motion, without the cost.** Cards and tiles have a hairline edge and a faint
  sheen from above; panels are translucent and grow in from 94 %; the media card takes a wash
  of colour from the album cover and crossfades it between tracks; play and pause morph into
  each other; the progress bar glides; quick settings tiles crossfade on and off; switching
  theme dissolves the old screen into the new one instead of jumping; numbers use fixed-width
  digits so speed and time do not jitter. Material easing curves throughout. Only
  GPU-composited properties move, and *Settings → Animations* turns all of it off on a unit
  that cannot keep up. The app never plays a click sound.

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
4. Tap the speed or car card once and allow location, so speed and trip work. The app never
   asks on its own. Over adb: `adb shell pm grant <package> android.permission.ACCESS_FINE_LOCATION`.
5. Allow media access so the player can see what is playing. Either tap **Allow access** on the
   home screen, or grant it over adb:

   ```bash
   adb shell cmd notification allow_listener \
     io.github.giannoccarol.corollalauncher.debug/io.github.giannoccarol.corollalauncher.media.MediaListenerService
   ```

   Drop `.debug` from the package name for release builds.
6. Set up the assistant: *Settings → Assistant*, choose the provider and paste its API key.
   Typing a key on the head unit is tedious, so you can instead put it in `local.properties`
   before building (that file is never committed):

   ```properties
   ASSISTANT_PROVIDER=anthropic
   ASSISTANT_API_KEY=sk-ant-...
   ASSISTANT_MODEL=claude-opus-5-5
   ```

   The app stores it encrypted on first start. **The key is then inside that APK**: do not share
   the file. Keys saved in the Monetine app cannot be copied over: Android keeps them encrypted
   with a key that never leaves the phone.
7. To make the **Recent apps** key work, enable *Corolla Launcher* under the system
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
| Set your name | Tap the name at the top left, or *Settings → Your name* |
| Open an app | Tap its icon in the bottom bar, in **Most used** or in **All apps** |
| See recent apps | Square key, top left (needs the accessibility service, see above) |
| Show the system bars | Swipe in from the top or bottom edge; they hide again by themselves |
| Pin or unpin a bottom bar app | Long press the icon |
| Turn on speed and trip | Tap the speed or car card and allow location |
| Switch media source | Tap the app chip at the top of the player |
| Set Home / Work address | Long press the row, or *Settings → Navigation* |
| Switch theme | The theme tile in quick settings, or *Settings → Theme* for all modes |
| Change language | *Settings → Language* |
| Turn animations off | *Settings → Animations* |
| Ask the assistant | Assistant button (top bar) or the wheel's voice button; tap the microphone again to stop |
| Type instead of speaking | *Type* in the assistant panel |

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
- **Speed and trip come from the unit's GPS antenna** and were tested only with simulated
  fixes. Many head units have a weak antenna; the app shows "Waiting for GPS" until a fix
  arrives. The trip restarts if the app process is restarted.
- **A small notification stays while location is on** ("Trip computer on"). Android requires
  it for apps that read GPS in the background; it is silent and low priority.
- **Wi-Fi and Bluetooth switch directly only on Android 9 and 11 or below** respectively, and
  the Wi-Fi panel can join a network only on Android 9 and below. Newer versions reserve both
  to the system, so there the panel hands over to the system Wi-Fi or Bluetooth screen.
- **The Bluetooth panel lists paired phones but cannot connect them**: Android has no public
  API for that. Many head units also run Bluetooth on a separate module that Android does not
  see at all; then the panel shows nothing useful and the unit's own Bluetooth app is the way.
- **The brightness slider needs a one-time grant** ("modify system settings"), and may do
  nothing on units whose backlight is driven by their own controller.
- **The Audio tile opens the system equalizer**, which head units often replace with their own
  DSP app; that app's package is still unknown (see [docs/head-unit.md](docs/head-unit.md)).
- **The assistant has not answered a real question yet.** Its whole request path was tested
  against the live Anthropic API with a deliberately wrong key (the provider's "invalid key"
  reply came back and was shown), but no valid key was used, so the request body, the answers
  and the actions are unverified. Speech recognition needs a recogniser on the device (the
  Google app provides one where Google services exist); the emulator has none, so voice input
  is untested too and the panel falls back to typing.
- **What the assistant sends, and to whom**: to the AI provider your words, the time, speed,
  trip distance, the track playing, whether home/work are saved (not the addresses) and the
  names of installed apps; the car's coordinates only when it asks "where am I". To Open-Meteo
  and OpenStreetMap the car's coordinates, only when a weather or places question needs them.
  Nothing is sent until you talk to it.
- **Web search is Anthropic's** (a server tool, same key, billed per search on top of tokens);
  with other providers the assistant cannot search the web. Open-Meteo and OpenStreetMap are
  free and keyless, with fair-use limits a car never reaches.
- **"Play music" depends on the music app** handling Android's standard play-from-search
  request; Spotify does, many radio apps do not.
- **Android Auto / CarPlay is untested on the unit.** The launcher only opens the unit's own
  projection app; it does not touch the connection. On some firmwares the stock launcher is
  what opens that app when the phone connects: with this launcher as Home, that may stop
  happening and the app has to be opened from its icon until we know its package and add an
  automatic start. Music played through Android Auto will probably not show in the media card.
- **Themed icons were checked only with the emulator's stock apps.** Brand logos and the
  silhouette fallback have not been seen with real Spotify, Maps or head unit apps yet; an app
  that gets the wrong symbol needs its package or label added to `IconGlyphs`. Icon pack
  support was tested with a small pack built for the purpose, not with a Play Store pack.
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
  trip/                   GPS speed, heading and trip computer, foreground service
  panels/                 the launcher's own Wi-Fi, Bluetooth, display and audio panels
  assistant/              voice assistant: speech, text-to-speech, prompt, tool loop, actions
  assistant/tools/        weather, places, location and action tools
  assistant/ai/           AI providers and the HTTP client (ported from Monetine)
  keys/                   accessibility service behind the top bar's system keys
  nav/                    navigation intents, recent destinations
  settings/               preferences, languages, settings screen
  theme/                  light/dark handling
  ui/                     base activity, full-screen handling, text dialog, motion
app/src/main/res/
  values/ values-night/   light and dark colors (same names in both)
  values-xx/              translations
docs/                     architecture, design tokens, head unit notes
art/                      source artwork (the original car photo)
scripts/                  translation check, emulator helper, car picture builder
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

- A live map in the navigation card (see the discussion in [docs/head-unit.md](docs/head-unit.md)).
- Turn-by-turn hints read from the navigation app's notification.
- Configurable cards: choose and arrange them (weather, radio presets).
- Accent color and night hours in Settings.
- Theme switching from the headlight signal, if the unit exposes it.
- Launching the stock radio, Bluetooth and reverse camera apps by name once their package
  names are known.

## Credits

- [AndroidX](https://developer.android.com/jetpack/androidx) (AppCompat, Core KTX,
  RecyclerView) by Google and the Android Open Source Project, Apache License 2.0.
- Build tooling: [Gradle](https://gradle.org) and the Android Gradle Plugin.
- Interface icons (`ic_*`) are drawn for this project. Original app icons and icon pack
  drawables shown at runtime belong to their apps and packs and are read from the device.
- Themed icon glyphs (`res/drawable/glyph_*.xml`, generated by `scripts/import_glyphs.py`):
  - brand logos from [Simple Icons](https://github.com/simple-icons/simple-icons) (16.37.0),
    CC0 1.0. The logos are trademarks of their owners, shown only to identify their apps;
  - symbols from Google's [Material Icons](https://github.com/google/material-design-icons),
    filled style, through the [@material-design-icons/svg](https://github.com/marella/material-design-icons)
    package (0.14.15), Apache License 2.0.
- The themed icon style follows Android 13's themed icons; the icon pack format is the
  appfilter.xml convention started by ADW Launcher and shared by Nova, Lawnchair and others.
- The layout takes general inspiration from modern in-car interfaces (card dashboard, large
  type, bottom bar, uniform light and dark themes). No third-party code, artwork or fonts were
  copied.
- The assistant's provider catalogue, HTTP client, API key vault and speech controller are
  ported from the owner's [Monetine](https://github.com/giannoccarol/monetine) app
  (`ai/AiProviders.kt`, `ai/LlmClient.kt`, `ai/LlmBodies.kt`, `data/settings/KeyVault.kt`,
  `voice/SpeechController.kt`), whose provider list follows pi-desktop. Rewritten to use
  Android's org.json and a worker thread instead of kotlinx.serialization and coroutines.
- Weather data by [Open-Meteo](https://open-meteo.com), CC BY 4.0.
- Places and addresses © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors,
  ODbL, through the [Overpass API](https://overpass-api.de) and
  [Nominatim](https://nominatim.org). The attribution is also shown in *Settings → About*.
- [JSON-java](https://github.com/stleary/JSON-java) (org.json), Public Domain, used only by unit
  tests (Android ships its own copy).
- The Toyota Corolla E120 photo (`art/corolla-e120.png`, resized into
  `res/drawable-*/car_corolla.webp`) was supplied by the owner of this project; its original
  source is not recorded. Toyota and Corolla are trademarks of Toyota Motor Corporation.
  Replace the photo before making the repository or the app public.

## License

Private project, all rights reserved. No license is granted.
