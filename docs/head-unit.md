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
| Screen is 1024x600 at 160 dpi | Typical for 7" units | Layout was designed and tested at this size |
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

## Findings log

Nothing yet: the unit has not been connected.
