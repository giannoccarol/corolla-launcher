#!/usr/bin/env bash
# Creates and starts an emulator shaped like the head unit: 1024x600 at 160 dpi, landscape.
#
#   scripts/emulator.sh create   # one-time: create the AVD (needs the system image below)
#   scripts/emulator.sh start    # boot it headless and wait until Android is ready
#   scripts/emulator.sh window   # boot it with a visible window
#   scripts/emulator.sh stop     # shut it down
#
# Requires the Android SDK (ANDROID_HOME, default ~/Android/Sdk) and a JDK 17+ for avdmanager.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AVD="headunit_1024x600"
IMAGE="system-images;android-30;default;x86_64"
CONFIG="$HOME/.android/avd/$AVD.avd/config.ini"

create() {
  echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" --force
  sed -i -e '/^hw.lcd.width/d' -e '/^hw.lcd.height/d' -e '/^hw.lcd.density/d' \
    -e '/^hw.initialOrientation/d' -e '/^skin\./d' -e '/^showDeviceFrame/d' \
    -e '/^hw.ramSize/d' -e '/^hw.keyboard=/d' "$CONFIG"
  cat >>"$CONFIG" <<'EOF'
hw.lcd.width=1024
hw.lcd.height=600
hw.lcd.density=160
hw.initialOrientation=landscape
skin.name=1024x600
skin.path=_no_skin
showDeviceFrame=no
hw.ramSize=2048
hw.keyboard=yes
EOF
  echo "Created $AVD"
}

start() {
  local window_flag="$1"
  # shellcheck disable=SC2086
  nohup "$SDK/emulator/emulator" -avd "$AVD" $window_flag -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect >/tmp/"$AVD".log 2>&1 &
  echo "Booting $AVD..."
  adb -e wait-for-device
  until [ "$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
  done
  echo "Ready: $(adb -e shell wm size | tr -d '\r'), $(adb -e shell wm density | tr -d '\r')"
}

case "${1:-}" in
  create) create ;;
  start) start "-no-window" ;;
  window) start "" ;;
  stop) adb -e emu kill ;;
  *)
    sed -n '2,9p' "$0"
    exit 1
    ;;
esac
