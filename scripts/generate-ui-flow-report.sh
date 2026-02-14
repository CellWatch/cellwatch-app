#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$REPO_ROOT/build/reports/ui-flow"
ANDROID_DIR="$REPORT_DIR/android"
IOS_DIR="$REPORT_DIR/ios"
LOG_DIR="$REPORT_DIR/logs"
MARKDOWN_FILE="$REPORT_DIR/UI_FLOW_REPORT.md"
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
  local command="$2"
  local log_file="$LOG_DIR/$name.log"
  local status_file="$LOG_DIR/$name.status"
  echo "==> $name"
  if /bin/bash -lc "cd \"$REPO_ROOT\" && $command" >"$log_file" 2>&1; then
    if [[ "$name" == ios-* ]] && grep -q "Executed 0 tests" "$log_file"; then
      echo "FAIL" >"$status_file"
    else
      echo "PASS" >"$status_file"
    fi
  else
    echo "FAIL" >"$status_file"
  fi
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

reset_android_flow() {
  local flow="$1"
  local remote_dir="$ANDROID_REMOTE_BASE_DIR/$flow"
  "$ADB_BIN" shell "rm -rf '$remote_dir' && mkdir -p '$remote_dir'" >/dev/null 2>&1 || true
  rm -rf "$ANDROID_DIR/$flow"
}

disable_android_animations() {
  "$ADB_BIN" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  "$ADB_BIN" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  "$ADB_BIN" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
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

disable_android_animations

reset_android_flow "onboarding-profile-entry"
run_step \
  "android-onboarding-profile-entry" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :androidTestApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.OnboardingRuntimeUiSmokeTest -Pandroid.testInstrumentationRunnerArguments.cellwatchRunOnboardingUiSmoke=1"
pull_android_flow "onboarding-profile-entry"

reset_android_flow "measurement-start-preflight"
run_step \
  "android-measurement-start-preflight" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :androidTestApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.MeasurementStartPreflightUiSmokeTest -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMeasurementStartPreflightUiSmoke=1"
pull_android_flow "measurement-start-preflight"

reset_android_flow "phase3-sequence-button"
run_step \
  "android-phase3-sequence-button" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :androidTestApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=edu.gatech.cc.cellwatch.androidtestapp.Phase3SequenceButtonUiSmokeTest -Pandroid.testInstrumentationRunnerArguments.cellwatchRunPhase3UiSmoke=1"
pull_android_flow "phase3-sequence-button"

run_step \
  "ios-onboarding-profile-entry-hosted" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :shared:verifyIosTestAppHostedOnboardingRuntimeSmoke"

run_step \
  "ios-onboarding-profile-entry-xcuitest" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :shared:verifyIosTestAppUiOnboardingFlowSmoke"
copy_ios_flow "onboarding-profile-entry-xcuitest"

run_step \
  "ios-measurement-start-preflight-xcuitest" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :shared:verifyIosTestAppUiMeasurementStartPreflightSmoke"
copy_ios_flow "measurement-start-preflight-xcuitest"

run_step \
  "ios-phase3-sequence-button" \
  "./gradlew --gradle-user-home \"$GRADLE_USER_HOME\" :shared:verifyIosTestAppHostedPhase3ButtonSmoke"
copy_ios_flow "phase3-sequence-button"

{
  echo "# UI Flow Report"
  echo
  echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  echo "_Scope: product-like onboarding, measurement-start preflight, and measurement-run (Phase 3 sequence) flow evidence. iOS hosted onboarding smoke is assertion-only (no screenshots); iOS visual evidence comes from XCUITest device screenshots._"
  echo
  render_flow_section "android" "onboarding-profile-entry" "Android: Onboarding Profile Entry Smoke"
  render_flow_section "android" "measurement-start-preflight" "Android: Measurement Start Preflight Smoke"
  render_flow_section "android" "phase3-sequence-button" "Android: Measurement Run Phase3 Sequence Smoke"
  render_flow_section "ios" "onboarding-profile-entry-xcuitest" "iOS: Onboarding Profile Entry Smoke (XCUITest)"
  render_flow_section "ios" "measurement-start-preflight-xcuitest" "iOS: Measurement Start Preflight Smoke (XCUITest)"
  render_flow_section "ios" "phase3-sequence-button" "iOS: Measurement Run Phase3 Sequence Smoke (XCUITest)"
} > "$MARKDOWN_FILE"

echo
echo "UI flow report generated:"
echo "  $MARKDOWN_FILE"
