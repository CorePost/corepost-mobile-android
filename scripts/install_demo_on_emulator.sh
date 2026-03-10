#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$(cd "$(dirname "$0")" && pwd)/_java_env.sh"
cd "$ROOT_DIR"

REVERSE_PORT="${COREPOST_DEMO_PORT:-}"

./gradlew assembleDebug
adb wait-for-device
if [[ -n "$REVERSE_PORT" ]]; then
  adb reverse "tcp:${REVERSE_PORT}" "tcp:${REVERSE_PORT}"
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.corepostemergencybutton.debug/com.example.corepostemergencybutton.MainActivity >/dev/null

echo "Debug APK installed on emulator."
echo "If the emulator is not running yet, start it first with: emulator -avd my_avd"
if [[ -z "$REVERSE_PORT" ]]; then
  echo "If your server runs on the host machine, set COREPOST_DEMO_PORT and rerun to configure adb reverse."
fi
