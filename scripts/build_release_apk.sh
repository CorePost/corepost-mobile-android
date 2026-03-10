#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$(cd "$(dirname "$0")" && pwd)/_java_env.sh"
cd "$ROOT_DIR"

./gradlew clean assembleRelease

echo "Release artifact:"
ls -lh app/build/outputs/apk/release
