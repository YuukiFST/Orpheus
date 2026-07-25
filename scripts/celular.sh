#!/usr/bin/env bash
# Open the Orpheus test emulator for manual QA. Agents: do not run this unless asked.
# Usage:
#   ./scripts/celular.sh              # boot emulator only
#   ./scripts/celular.sh --install    # install existing debug APK + launch
#   ./scripts/celular.sh --rebuild    # build debug APK, then install + launch
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${TMPDIR:-/tmp}/orpheus-android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools:$PATH"

AVD="${ORPHEUS_AVD:-orpheus_test}"
PKG="${ORPHEUS_DEBUG_PKG:-com.yuukifst.orpheus.debug}"
ACTIVITY="$PKG/com.yuukifst.orpheus.MainActivity"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

if [[ "${1:-}" == "--rebuild" ]]; then
  "$ROOT/scripts/build.sh" :app:assembleDebug -Porpheus.enableAbiSplits=false
  set -- --install
fi

if [[ ! -x "$ADB" || ! -x "$EMULATOR" ]]; then
  echo "SDK tools missing under $ANDROID_SDK_ROOT" >&2
  echo "Expected adb + emulator. Fix sdk.dir / ANDROID_SDK_ROOT first." >&2
  exit 1
fi

device_online() {
  "$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {found=1} END{exit !found}'
}

if ! device_online; then
  echo "Starting AVD: $AVD"
  # ponytail: single AVD name; add ORPHEUS_AVD if you keep more than one
  nohup "$EMULATOR" -avd "$AVD" -no-audio -gpu auto >/tmp/orpheus-emulator.log 2>&1 &
  echo "Waiting for device…"
  "$ADB" wait-for-device
  # boot_completed can lag after wait-for-device
  for _ in $(seq 1 60); do
    boot="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    [[ "$boot" == "1" ]] && break
    sleep 2
  done
fi

echo "Device ready: $("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"

if [[ "${1:-}" == "--install" ]]; then
  APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  if [[ ! -f "$APK" ]]; then
    echo "No debug APK. Run: ./scripts/celular.sh --rebuild" >&2
    exit 1
  fi
  echo "Installing $APK"
  "$ADB" install -r "$APK"
  echo "Launching $ACTIVITY"
  "$ADB" shell am start -n "$ACTIVITY" >/dev/null
fi

echo "Emulator up. Test manually. Log: /tmp/orpheus-emulator.log"
