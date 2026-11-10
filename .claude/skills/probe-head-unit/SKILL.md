---
name: probe-head-unit
description: Collect the real hardware and software facts of the car head unit over adb (true Android version, RAM, screen, stock app package names, media sessions, launcher options) and record them in docs/head-unit.md. Use when the head unit is connected for the first time, when something works on the emulator but not on the unit, or when asked what the unit really is.
---

# Probe the head unit

Cheap head units routinely misreport their specs in the settings screen. These commands read
the real values. Everything here is **read-only**; do not change anything on the unit as part
of probing.

Confirm with `adb devices` that the target is the head unit and not an emulator, and use
`adb -d` (USB) or `adb -s <serial>` for every command.

## Identity and hardware

```bash
adb -d shell getprop ro.build.version.release      # Android version actually running
adb -d shell getprop ro.build.version.sdk          # API level (must be >= 24)
adb -d shell getprop ro.product.model
adb -d shell getprop ro.product.manufacturer
adb -d shell getprop ro.board.platform             # chipset family
adb -d shell getprop ro.product.cpu.abilist        # arm64-v8a or only armeabi-v7a
adb -d shell getprop ro.config.low_ram             # "true" can disable notification listeners
adb -d shell cat /proc/meminfo | head -3           # real RAM
adb -d shell cat /proc/cpuinfo | grep -m1 -i -e hardware -e "model name"
adb -d shell wm size
adb -d shell wm density
adb -d shell df -h /data
```

If the settings screen says Android 13 and `ro.build.version.sdk` says 27, the SDK value is
the truth.

## Installed apps

```bash
adb -d shell pm list packages -s     # system apps: stock launcher, radio, BT, camera
adb -d shell pm list packages -3     # user-installed apps
adb -d shell cmd package resolve-activity --brief -c android.intent.category.HOME \
  -a android.intent.action.MAIN      # current default launcher
```

Look for the stock radio, Bluetooth music, phone, reverse camera, equalizer and phone-link
(CarPlay / Android Auto) apps. Record their package names: the launcher needs them to offer
direct shortcuts.

## Media and audio

Start the stock radio, then Bluetooth music, then a streaming app, and after each:

```bash
adb -d shell dumpsys media_session | grep -A6 "Sessions Stack"
adb -d shell dumpsys audio | grep -m5 -i "STREAM_MUSIC" -A4
```

- An app missing from the sessions stack while it plays has no media session: the launcher's
  player cannot show it, only send it media keys.
- Change the volume with the knob and re-run the audio dump. If `STREAM_MUSIC` does not move,
  the unit controls loudness outside Android and the dock volume buttons will not work.

## Launcher and listeners

```bash
adb -d shell am start -a android.settings.HOME_SETTINGS                    # does it open?
adb -d shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
adb -d shell cmd notification allow_listener \
  io.github.giannoccarol.corollalauncher.debug/io.github.giannoccarol.corollalauncher.media.MediaListenerService
```

The first two open screens on the unit, and the third grants media access: ask the user
before running them, since they are the only non-read-only steps.

## Hardware buttons and headlights

```bash
adb -d shell getevent -lt          # press each panel button; note the key codes, Ctrl+C to stop
adb -d logcat -c && adb -d logcat | grep -i -e broadcast -e "ill" -e "light"   # toggle headlights
```

## Record the results

Update `docs/head-unit.md`: tick the open questions that are now answered, correct the
assumptions table, and add a dated entry under "Findings log" with the raw values. If a
finding invalidates something in the README "Known limits" section, update that too.
