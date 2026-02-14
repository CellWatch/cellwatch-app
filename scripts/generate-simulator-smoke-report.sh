#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$REPO_ROOT/build/reports/simulator-smoke"
ANDROID_DIR="$REPORT_DIR/android"
IOS_DIR="$REPORT_DIR/ios"
LOG_DIR="$REPORT_DIR/logs"
MARKDOWN_FILE="$REPORT_DIR/SIMULATOR_SMOKE_REPORT.md"
GRADLE_USER_HOME="${CELLWATCH_GRADLE_USER_HOME:-$REPO_ROOT/.gradle-scripts}"

ADB_BIN="${ADB_BIN:-/Users/jeff/Library/Android/sdk/platform-tools/adb}"
ANDROID_REMOTE_BASE_DIR="/sdcard/Download/cellwatch-ui-flow/android"
IOS_TMP_BASE_DIR="/tmp/cellwatch-ui-flow/ios"

mkdir -p "$ANDROID_DIR" "$IOS_DIR" "$LOG_DIR"
mkdir -p "$GRADLE_USER_HOME"
rm -rf "$ANDROID_DIR"/* "$IOS_DIR"/*
rm -rf "$LOG_DIR"/*
rm -rf "$IOS_TMP_BASE_DIR"

run_step() {
  local name="$1"
  shift
  local log_file="$LOG_DIR/$name.log"
  local status_file="$LOG_DIR/$name.status"
  echo "==> $name"
  pushd "$REPO_ROOT" >/dev/null
  if "$@" >"$log_file" 2>&1; then
    if [[ "$name" == ios-* ]] && grep -q "Executed 0 tests" "$log_file"; then
      echo "FAIL" >"$status_file"
    else
      echo "PASS" >"$status_file"
    fi
  else
    echo "FAIL" >"$status_file"
  fi
  popd >/dev/null
}

pull_android_flow() {
  local flow="$1"
  local remote_dir="$ANDROID_REMOTE_BASE_DIR/$flow"
  local target_dir="$ANDROID_DIR/$flow"
  mkdir -p "$target_dir"
  "$ADB_BIN" pull "$remote_dir" "$target_dir" >/dev/null 2>&1 || true
  if [[ -d "$target_dir/$flow" ]]; then
    cp -R "$target_dir/$flow"/. "$target_dir"/ 2>/dev/null || true
    rm -rf "$target_dir/$flow"
  fi
}

copy_ios_flow() {
  local flow="$1"
  local source_dir="$IOS_TMP_BASE_DIR/$flow"
  local target_dir="$IOS_DIR/$flow"
  if [[ -d "$source_dir" ]]; then
    mkdir -p "$target_dir"
    cp -R "$source_dir"/. "$target_dir"/
  fi
}

render_flow_section() {
  local platform="$1"
  local flow="$2"
  local title="$3"
  local status_file="$LOG_DIR/${platform}-${flow}.status"
  local log_file="$LOG_DIR/${platform}-${flow}.log"
  local image_dir
  if [[ "$platform" == "android" ]]; then
    image_dir="$ANDROID_DIR/$flow"
  else
    image_dir="$IOS_DIR/$flow"
  fi

  local status="FAIL"
  if [[ -f "$status_file" ]]; then
    status="$(cat "$status_file")"
  fi

  echo "## $title"
  echo
  echo "- Result: **$status**"
  echo "- Flow key: \`$flow\`"
  echo
  echo "### Log Excerpt"
  echo
  echo '```text'
  if [[ -f "$log_file" ]]; then
    tail -n 35 "$log_file"
  else
    echo "No log file found."
  fi
  echo '```'
  echo
  echo "### Screenshots"
  echo
  local image_count=0
  while IFS= read -r img; do
    image_count=$((image_count + 1))
    local rel="${img#$REPORT_DIR/}"
    local label="$(basename "$img")"
    echo "#### $label"
    echo
    echo "![$label]($rel)"
    echo
  done < <(find "$image_dir" -type f -name "*.png" 2>/dev/null | sort)
  if [[ $image_count -eq 0 ]]; then
    echo "_No screenshots found for this flow._"
    echo
  fi
}

run_step \
  "android-phase3-sequence-button" \
  ./gradlew \
  --gradle-user-home "$GRADLE_USER_HOME" \
  :androidTestApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.Phase3SequenceButtonUiSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.cellwatchRunPhase3UiSmoke=1
pull_android_flow "phase3-sequence-button"

run_step \
  "ios-phase3-sequence-button" \
  ./gradlew \
  --gradle-user-home "$GRADLE_USER_HOME" \
  :shared:verifyIosTestAppHostedPhase3ButtonSmoke
copy_ios_flow "phase3-sequence-button"

{
  echo "# Simulator Smoke Report"
  echo
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  echo "_Scope: cross-platform simulator smoke for Phase 3 sequence button path._"
  echo
  render_flow_section "android" "phase3-sequence-button" "Android: Phase3 Sequence Button Smoke"
  render_flow_section "ios" "phase3-sequence-button" "iOS: Phase3 Sequence Button Smoke"
} > "$MARKDOWN_FILE"

echo
echo "Simulator smoke report generated:"
echo "  $MARKDOWN_FILE"
