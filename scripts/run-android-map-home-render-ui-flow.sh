#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$REPO_ROOT/build/reports/ui-flow"
ANDROID_DIR="$REPORT_DIR/android"
LOG_DIR="$REPORT_DIR/logs"
FLOW="map-home-render"
ADB_BIN="${ADB_BIN:-/Users/jeff/Library/Android/sdk/platform-tools/adb}"
ANDROID_REMOTE_BASE_DIR="/sdcard/Download/cellwatch-ui-flow/android"
GRADLE_USER_HOME="${CELLWATCH_GRADLE_USER_HOME:-$REPO_ROOT/.gradle-scripts}"
MAP_SIM_LAT="${CELLWATCH_MAP_SIM_LAT:-33.778462}"
MAP_SIM_LON="${CELLWATCH_MAP_SIM_LON:--84.390123}"

mkdir -p "$ANDROID_DIR/$FLOW" "$LOG_DIR" "$GRADLE_USER_HOME"
rm -rf "$ANDROID_DIR/$FLOW"/*
"$ADB_BIN" shell "rm -rf '$ANDROID_REMOTE_BASE_DIR/$FLOW' && mkdir -p '$ANDROID_REMOTE_BASE_DIR/$FLOW'" >/dev/null 2>&1 || true
# Midtown Atlanta (85 5th St NW) emulator geo seed for deterministic map-home captures.
"$ADB_BIN" emu geo fix "$MAP_SIM_LON" "$MAP_SIM_LAT" >/dev/null 2>&1 || true

LOG_FILE="$LOG_DIR/android-$FLOW.log"
STATUS_FILE="$LOG_DIR/android-$FLOW.status"

if /bin/bash -lc "cd \"$REPO_ROOT\" && ./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :androidTestApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.MapHomeRenderUiSmokeTest -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMapHomeRenderUiSmoke=1" >"$LOG_FILE" 2>&1; then
  echo "PASS" >"$STATUS_FILE"
else
  echo "FAIL" >"$STATUS_FILE"
fi

"$ADB_BIN" pull "$ANDROID_REMOTE_BASE_DIR/$FLOW" "$ANDROID_DIR/$FLOW" >/dev/null 2>&1 || true
if [[ -d "$ANDROID_DIR/$FLOW/$FLOW" ]]; then
  cp -R "$ANDROID_DIR/$FLOW/$FLOW"/. "$ANDROID_DIR/$FLOW"/ 2>/dev/null || true
  rm -rf "$ANDROID_DIR/$FLOW/$FLOW"
fi

echo "Map-home UI-flow status: $(cat "$STATUS_FILE")"
echo "Screenshots: $ANDROID_DIR/$FLOW"
echo "Log: $LOG_FILE"
