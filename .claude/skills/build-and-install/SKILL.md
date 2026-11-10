---
name: build-and-install
description: Build the launcher APK and install it on the 1024x600 emulator or on the real head unit over adb, then set it up (default launcher, media access) and capture screenshots. Use when asked to build, run, install, deploy, try or screenshot the app.
---

# Build and install

## 1. Build

Gradle needs JDK 17 or 21. Check `java -version`; if it is newer, export a suitable
`JAVA_HOME` first (for example a JDK under `~/.gradle/jdks/`).

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 scripts/check_translations.py
```

All three must pass before installing. The APK is
`app/build/outputs/apk/debug/app-debug.apk`; the debug package name is
`io.github.giannoccarol.corollalauncher.debug` (release builds drop `.debug`).

## 2. Pick the target

Run `adb devices`.

- **Emulator**: if none is running, `scripts/emulator.sh start` (headless) or
  `scripts/emulator.sh window`. If the AVD does not exist yet, `scripts/emulator.sh create`.
  Use `adb -e ...` for every command.
- **Head unit**: connect by USB, or `adb connect <ip>:5555` when Wi-Fi debugging is on.
  Use `adb -d ...` (USB) or `adb -s <ip>:5555 ...`.

Never install on a device the user did not ask for when several are attached: ask which one.

## 3. Install and set up

```bash
PKG=io.github.giannoccarol.corollalauncher.debug
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e shell cmd notification allow_listener \
  $PKG/io.github.giannoccarol.corollalauncher.media.MediaListenerService
adb -e shell am start -n $PKG/io.github.giannoccarol.corollalauncher.home.HomeActivity
```

To make the dock's Recent apps key work (this replaces any accessibility service already
enabled, so on the real unit check `adb shell settings get secure enabled_accessibility_services`
first and ask the user):

```bash
adb -e shell settings put secure enabled_accessibility_services \
  $PKG/io.github.giannoccarol.corollalauncher.keys.NavKeyService
adb -e shell settings put secure accessibility_enabled 1
```

To make it the default home screen on Android 10+:

```bash
adb -e shell cmd package set-home-activity \
  $PKG/io.github.giannoccarol.corollalauncher.home.HomeActivity
```

On the real head unit, ask the user before changing the default launcher or disabling the
stock one, and follow `docs/head-unit.md` if the unit refuses.

To give the app location and drive it on the emulator (speed in knots as the last argument;
`geo fix` sends no bearing, so the heading stays N):

```bash
adb -e shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
for i in $(seq 1 60); do
  adb -e emu geo fix "$(python3 -c "print(18.17+$i*0.0001)")" 40.35 50 8 24; sleep 1
done
```

## 4. Verify

```bash
adb -e exec-out screencap -p > /tmp/home.png      # then look at the image
adb -e logcat -d -s AndroidRuntime:E | tail -20   # must show no crash
```

Check every visible change in **both themes** (tap the sun/moon button, top right) and, for
text changes, in at least one non-English language (*Settings → Language*).

`adb shell uiautomator dump /sdcard/ui.xml` followed by `adb shell cat /sdcard/ui.xml` gives
the on-screen texts and their bounds, which is the reliable way to find tap coordinates.

## 5. Report

Say which device it ran on. A result from the emulator is not a result for the head unit:
keep the "Known limits" section of the README accurate.
