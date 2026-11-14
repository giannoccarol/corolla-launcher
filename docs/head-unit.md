# Head unit notes

Everything known, assumed and still unknown about the device this launcher targets. Update it
whenever the real unit tells us something.

## The device

- **Product**: Podofo Android car stereo for Toyota Corolla E120/E130 and BYD F3.
- **Listing claims**: quad-core, 4 GB RAM, 64 GB storage, wireless CarPlay and Android Auto,
  DSP with 32-band EQ, RDS, Wi-Fi, GPS.
- **Front panel**: 7" touchscreen, two knobs (power/volume, tune/select), hardware buttons
  MENU, MUTE, NAVI, BACK, BT, MOD.
- **Car**: Toyota Corolla E120, 2005.

## Assumptions the code makes (unverified)

| Assumption | Why | Where it matters |
| --- | --- | --- |
| Screen is about 1024x600 px | Typical for 7" units | Only the emulator; `UiScale` adapts the density to any panel |
| Real Android version is 8-10 | Cheap units often report a newer version than they run | `minSdk` 24 |
| Real RAM is 1-2 GB | Same reason | No heavy libraries, icons cached once |
| The unit lets a third-party launcher be the default | Usually true | Whole project |

## Open questions

Run the `probe-head-unit` skill (`.claude/skills/probe-head-unit/SKILL.md`) once the unit is
reachable over adb, and record the answers here.

- [ ] Real Android version, SDK level, CPU ABI, RAM, screen size and density.
- [ ] Is the device flagged low-RAM (which can disable notification listeners on Android 8)?
- [ ] Can the default launcher be changed from the stock settings?
- [ ] Package names of the stock radio, Bluetooth music, phone and reverse camera apps.
- [ ] Do the stock radio and Bluetooth apps publish a media session?
- [ ] Does Android's music stream volume change the actual loudness?
- [ ] What do the hardware buttons (NAVI, BT, MOD) launch, and can they be remapped?
- [ ] Is there a broadcast for headlights on/off (often called ILL), to switch theme with it?
- [ ] Does full screen really hide the unit's top bar, or is that bar an overlay?
- [ ] With the bar hidden, do the panel's hardware BACK and MENU buttons cover navigation?
- [ ] Can the accessibility service for the Recent apps key be enabled on this firmware?
- [ ] Does the unit have Google Play services (needed for an embedded Google map)?
- [ ] Package name of the unit's DSP / equalizer app, for the Audio tile.
- [ ] Package and label of the Android Auto / CarPlay app; is it recognised by `ProjectionApps`?
- [ ] With this launcher as Home, does connecting the phone still open Android Auto by itself?
- [ ] Does the projection app publish a media session while Android Auto plays music?
- [ ] Is the GPS antenna connected and how long does the first fix take?
- [ ] What does the wheel's voice button send (key code or intent)? Does it reach the launcher?
- [ ] Is there a speech recogniser (`adb shell cmd package query-services -a android.speech.RecognitionService`)?
- [ ] Is there a text-to-speech engine, and does it speak Italian?
- [ ] Does the built-in microphone pick up the driver clearly?
- [ ] Are the animations smooth on the unit, or should they ship off by default?
- [ ] Does the unit beep on every touch by itself, and where is that turned off?
- [ ] Can the unit join the phone's hotspot, and does the phone reach it there (some hotspots
      isolate clients)? Settings → Set up from your phone shows the address it found.
- [ ] Does a Favourites tap place the call (`ACTION_CALL`) through the unit's Bluetooth phone
      app, or only open a dialer? Are the phone's contacts visible to Android's contact picker?

## If the default launcher cannot be changed

Some firmwares pin their own launcher. Things to try, least invasive first:

1. Press the hardware Home/MENU button after installing: Android may show a chooser.
2. Open the system screen directly:
   `adb shell am start -a android.settings.HOME_SETTINGS`
3. Set it over adb (Android 10+):
   `adb shell cmd package set-home-activity io.github.giannoccarol.corollalauncher.debug/io.github.giannoccarol.corollalauncher.home.HomeActivity`
4. Look in the unit's factory settings menu (usually behind a PIN) for a launcher option.
5. As a last resort, disable the stock launcher for the current user:
   `adb shell pm disable-user --user 0 <stock.launcher.package>`
   Do this only after confirming the new launcher starts on boot, and note the package name
   here so it can be re-enabled with `pm enable`.

## Live map in the home screen (not built yet)

Two ways to put a real map on the home screen (there is no map card at the moment), both used in the owner's Cruscotto project
(`~/Documenti/Projects/Cruscotto`, `car/GoogleCarMap.kt` and `car/NavPanes.kt`):

| | Google Maps SDK | MapLibre + OpenFreeMap |
| --- | --- | --- |
| Needs | Google Play services on the unit, an API key in `local.properties` | Nothing; tiles from `tiles.openfreemap.org`, no key |
| Looks | Google's map, traffic, 3D buildings, custom grey style | OpenStreetMap, restyled grey |
| Cost on the unit | Rendering inside Play services; light APK | Native renderer in the APK (about 10-20 MB per CPU type) |
| Offline | Cached areas only | Cached areas only |

Cruscotto uses Google Maps when a key is present and falls back to MapLibre otherwise. Both
need mobile data or Wi-Fi in the car, and both cost GPU time that the weak head unit also needs
for everything else, so the map should stay a passive view (follow the car, no tilt, no 3D)
and pause when the home screen is hidden. Turn-by-turn hints can be read from Google Maps'
navigation notification with the notification listener the app already has, as Cruscotto's
`data/NotificationSource.kt` does.

## Findings log

- **2026-11 first drive (owner's report, no adb)**: everything looked too small and crowded,
  the bars squashed. The map card was dropped and the interface size added, Large by default.
  Music played through Android Auto did not appear in the media card. *Settings → Device info*
  now shows the facts to collect next without a cable: screen, Android version, projection
  app, media sessions and its notifications while it plays.
