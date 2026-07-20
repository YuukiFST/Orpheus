#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_SDK_ROOT="${TMPDIR:-/tmp}/orpheus-android-sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
mkdir -p "$ANDROID_SDK_ROOT"
echo "sdk.dir=$ANDROID_SDK_ROOT" > "$ROOT/local.properties"
cd "$ROOT"
exec nix run .# --impure -- "$@"
