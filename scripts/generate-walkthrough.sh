#!/usr/bin/env bash
#
# Screenshot-backed PDF walkthroughs of the product vertical slice.
#
# Successor to generate-ui-flow-report.sh, which covers the *harness* flows and
# emits Markdown. This drives the product navigation graph and emits PDFs.
#
#   ./scripts/generate-walkthrough.sh            # both platforms
#   ./scripts/generate-walkthrough.sh ios        # one platform
#
# Android needs a running emulator; iOS needs a booted simulator. Both must
# already have the app's onboarding completed - the walkthrough documents the
# measurement slice, not first-run setup.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/build/reports/walkthrough"
RENDER="$REPO_ROOT/scripts/walkthrough/render_pdf.py"
SIM_ID="${CELLWATCH_SIM_ID:-B2875856-6CE2-40C6-896D-134FAA277283}"
SIM_LABEL="${CELLWATCH_SIM_LABEL:-iOS Simulator}"
AVD_LABEL="${CELLWATCH_AVD_LABEL:-Android emulator}"
TARGET="${1:-both}"

mkdir -p "$OUT_DIR"

run_android() {
  echo "==> Android capture"
  python3 "$REPO_ROOT/scripts/walkthrough/capture_android.py" \
    --out "$OUT_DIR/android" --device-label "$AVD_LABEL"
  python3 "$RENDER" "$OUT_DIR/android/manifest.json" \
    -o "$OUT_DIR/CellWatch-Walkthrough-Android.pdf"
}

run_ios() {
  echo "==> iOS capture (XCUITest)"
  # The UI tests live on their own scheme; the app scheme cannot run them.
  ( cd "$REPO_ROOT/iosTestApp" && xcodebuild test \
      -project iosTestApp.xcodeproj -scheme iosTestAppUiSmoke \
      -destination "platform=iOS Simulator,id=$SIM_ID" \
      -only-testing:iosTestAppUITests/ProductWalkthroughUiTests \
      >"$OUT_DIR/ios-xcuitest.log" 2>&1 ) || {
        echo "XCUITest failed; see $OUT_DIR/ios-xcuitest.log" >&2; return 1; }
  python3 "$REPO_ROOT/scripts/walkthrough/build_ios_manifest.py" \
    --out "$OUT_DIR/ios" --device-label "$SIM_LABEL"
  python3 "$RENDER" "$OUT_DIR/ios/manifest.json" \
    -o "$OUT_DIR/CellWatch-Walkthrough-iOS.pdf"
}

case "$TARGET" in
  android) run_android ;;
  ios) run_ios ;;
  both) run_android; run_ios ;;
  *) echo "usage: $0 [android|ios|both]" >&2; exit 2 ;;
esac

echo
echo "Walkthroughs:"
ls -1 "$OUT_DIR"/*.pdf
