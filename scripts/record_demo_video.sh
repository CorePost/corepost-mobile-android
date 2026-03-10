#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

OUTPUT_PATH="${1:-docs/media/video/demo-my_avd.mp4}"
DURATION_SECONDS="${COREPOST_DEMO_VIDEO_SECONDS:-15}"
REMOTE_PATH="/sdcard/corepost-demo.mp4"

mkdir -p "$(dirname "$OUTPUT_PATH")"

adb wait-for-device
adb shell rm -f "$REMOTE_PATH"
adb shell screenrecord --time-limit "$DURATION_SECONDS" "$REMOTE_PATH"
adb pull "$REMOTE_PATH" "$OUTPUT_PATH" >/dev/null
adb shell rm -f "$REMOTE_PATH"

echo "Recorded demo video:"
ls -lh "$OUTPUT_PATH"
