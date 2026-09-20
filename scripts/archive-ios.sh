#!/usr/bin/env bash
#
# Archives and exports an .ipa for TestFlight / App Store.
#
#   ./scripts/archive-ios.sh              # Release  -> hosted TESTING Supabase
#   ./scripts/archive-ios.sh appstore     # AppStore -> LIVE Supabase
#
# The scheme decides the backend, so it is spelled out on every run rather
# than left to whatever Xcode last had selected:
#
#   iosTestApp          archives Release   -> CELLWATCH_DEFAULT_SUPABASE_MODE=TESTING
#   iosTestAppAppStore  archives AppStore  -> CELLWATCH_DEFAULT_SUPABASE_MODE=LIVE
#
# Uploading is not automated. It needs App Store Connect credentials and it
# publishes; the command is printed at the end for a human to run.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-release}"
OUT_DIR="${CELLWATCH_ARCHIVE_DIR:-$REPO_ROOT/build/ios-archive}"

case "$TARGET" in
  release)  SCHEME="iosTestApp";         BACKEND="hosted TESTING Supabase" ;;
  appstore) SCHEME="iosTestAppAppStore"; BACKEND="LIVE Supabase" ;;
  *) echo "usage: $0 [release|appstore]" >&2; exit 2 ;;
esac

PLIST="$REPO_ROOT/iosTestApp/App/Info.plist"
SHORT_VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PLIST")"
BUILD_NUMBER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$PLIST")"

echo "==> scheme      $SCHEME"
echo "==> backend     $BACKEND"
echo "==> version     $SHORT_VERSION ($BUILD_NUMBER)"
echo
# App Store Connect rejects a build number it has already seen for this
# version string, and it does so after the upload rather than before it.
if [[ "${CELLWATCH_ARCHIVE_ASSUME_YES:-}" == "1" ]]; then
  echo "(build number confirmed via CELLWATCH_ARCHIVE_ASSUME_YES)"
else
  read -r -p "Is build $BUILD_NUMBER unused for version $SHORT_VERSION? [y/N] " reply
  [[ "$reply" == "y" || "$reply" == "Y" ]] || {
    echo "Bump CFBundleVersion in $PLIST first." >&2
    exit 1
  }
fi

mkdir -p "$OUT_DIR"
ARCHIVE="$OUT_DIR/$SCHEME-$SHORT_VERSION-$BUILD_NUMBER.xcarchive"

echo "==> archiving"
( cd "$REPO_ROOT/iosTestApp" && xcodebuild archive \
    -project iosTestApp.xcodeproj \
    -scheme "$SCHEME" \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE" \
    -allowProvisioningUpdates )

echo "==> exporting"
( cd "$REPO_ROOT/iosTestApp" && xcodebuild -exportArchive \
    -archivePath "$ARCHIVE" \
    -exportPath "$OUT_DIR" \
    -exportOptionsPlist exportOptions.plist \
    -allowProvisioningUpdates )

# What actually got packaged, with secrets reduced to a prefix and a length.
APP="$ARCHIVE/Products/Applications/iosTestApp.app"
PROPS="$APP/cellwatch.runtime.properties"
if [[ -f "$PROPS" ]]; then
  echo
  echo "==> packaged runtime config"
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    key="${line%%=*}"; value="${line#*=}"
    case "$key" in
      *KEY|*TOKEN|*SECRET) echo "    $key = <${value:0:3}… ${#value} chars>" ;;
      *) echo "    $key = $value" ;;
    esac
  done < "$PROPS"
fi

echo
echo "Exported to $OUT_DIR"
echo "Upload with:"
echo "  xcrun altool --upload-app -f \"$OUT_DIR/iosTestApp.ipa\" -t ios \\"
echo "    --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>"
