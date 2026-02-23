#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$REPO_ROOT/build/reports/ui-flow"
IOS_DIR="$REPORT_DIR/ios"
LOG_DIR="$REPORT_DIR/logs"
FLOW="map-home-render-xcuitest"
IOS_TMP_BASE_DIR="/tmp/cellwatch-ui-flow/ios"
GRADLE_USER_HOME="${CELLWATCH_GRADLE_USER_HOME:-$REPO_ROOT/.gradle-scripts}"
MAP_SIM_LAT="${CELLWATCH_MAP_SIM_LAT:-33.778462}"
MAP_SIM_LON="${CELLWATCH_MAP_SIM_LON:--84.390123}"

mkdir -p "$IOS_DIR/$FLOW" "$LOG_DIR" "$GRADLE_USER_HOME"
rm -rf "$IOS_DIR/$FLOW"/*
rm -rf "$IOS_TMP_BASE_DIR/$FLOW"

# Midtown Atlanta (85 5th St NW) simulator geo seed for deterministic map-home captures.
xcrun simctl location booted set "$MAP_SIM_LAT" "$MAP_SIM_LON" >/dev/null 2>&1 || \
  xcrun simctl location booted set "$MAP_SIM_LAT,$MAP_SIM_LON" >/dev/null 2>&1 || true

LOG_FILE="$LOG_DIR/ios-$FLOW.log"
STATUS_FILE="$LOG_DIR/ios-$FLOW.status"

if /bin/bash -lc "cd \"$REPO_ROOT\" && ./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :shared:verifyIosTestAppUiMapHomeRenderFlowSmoke" >"$LOG_FILE" 2>&1; then
  if grep -q "Executed 0 tests" "$LOG_FILE"; then
    echo "FAIL" >"$STATUS_FILE"
  else
    echo "PASS" >"$STATUS_FILE"
  fi
else
  echo "FAIL" >"$STATUS_FILE"
fi

if [[ -d "$IOS_TMP_BASE_DIR/$FLOW" ]]; then
  cp -R "$IOS_TMP_BASE_DIR/$FLOW"/. "$IOS_DIR/$FLOW"/
fi

echo "Map-home iOS UI-flow status: $(cat "$STATUS_FILE")"
echo "Screenshots: $IOS_DIR/$FLOW"
echo "Log: $LOG_FILE"
