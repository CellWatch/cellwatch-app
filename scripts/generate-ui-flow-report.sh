#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$REPO_ROOT/build/reports/ui-flow"
ANDROID_DIR="$REPORT_DIR/android"
IOS_DIR="$REPORT_DIR/ios"
IOS_ATTACHMENTS_DIR="$IOS_DIR/attachments"
IOS_SCREENSHOTS_DIR="$IOS_DIR/screenshots"
MARKDOWN_FILE="$REPORT_DIR/UI_FLOW_REPORT.md"
GRADLE_USER_HOME="${CELLWATCH_GRADLE_USER_HOME:-$REPO_ROOT/.gradle-scripts}"

ADB_BIN="${ADB_BIN:-/Users/jeff/Library/Android/sdk/platform-tools/adb}"
ANDROID_REMOTE_SHOT_DIR="/sdcard/Download/cellwatch-ui-flow/android"

mkdir -p "$ANDROID_DIR" "$IOS_DIR"
mkdir -p "$GRADLE_USER_HOME"
rm -rf "$ANDROID_DIR"/* "$IOS_ATTACHMENTS_DIR" "$IOS_SCREENSHOTS_DIR"
rm -rf /tmp/cellwatch-ui-flow/ios

echo "==> Android UI smoke with simulated button tap"
(cd "$REPO_ROOT" && ./gradlew --gradle-user-home "$GRADLE_USER_HOME" :androidTestApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.Phase3SequenceButtonUiSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.cellwatchRunPhase3UiSmoke=1)

echo "==> Pulling Android screenshots"
"$ADB_BIN" pull "$ANDROID_REMOTE_SHOT_DIR" "$ANDROID_DIR" >/dev/null

echo "==> iOS hosted Phase3 button smoke (simulated tap)"
(cd "$REPO_ROOT" && ./gradlew --gradle-user-home "$GRADLE_USER_HOME" :shared:verifyIosTestAppHostedPhase3ButtonSmoke)

echo "==> Collecting iOS screenshots"
if [[ -d "/tmp/cellwatch-ui-flow/ios" ]]; then
  mkdir -p "$IOS_SCREENSHOTS_DIR"
  cp -R /tmp/cellwatch-ui-flow/ios/. "$IOS_SCREENSHOTS_DIR"/
fi

{
  echo "# UI Flow Report"
  echo
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  echo "## Android (Phase3 Button UI Smoke)"
  echo
  android_count=0
  while IFS= read -r img; do
    android_count=$((android_count + 1))
    rel="${img#$REPORT_DIR/}"
    label="$(basename "$img")"
    echo "### $label"
    echo
    echo "![$label]($rel)"
    echo
  done < <(find "$ANDROID_DIR" -type f -name "*.png" | sort)
  if [[ $android_count -eq 0 ]]; then
    echo "_No Android screenshots found._"
  fi

  echo "## iOS (Harness UI Smoke)"
  echo
  ios_count=0
  while IFS= read -r img; do
    ios_count=$((ios_count + 1))
    rel="${img#$REPORT_DIR/}"
    label="$(basename "$img")"
    echo "### $label"
    echo
    echo "![$label]($rel)"
    echo
  done < <(find "$IOS_SCREENSHOTS_DIR" -type f \( -name "*.png" -o -name "*.jpg" -o -name "*.jpeg" \) 2>/dev/null | sort)
  if [[ $ios_count -eq 0 ]]; then
    echo "_No iOS screenshots found._"
  fi
} > "$MARKDOWN_FILE"

echo
echo "UI flow report generated:"
echo "  $MARKDOWN_FILE"
