#!/usr/bin/env bash
set -euo pipefail

# Deterministically refresh a local MsakShared.xcframework from a producer zip.
# - Verifies SHA256 against sidecar file before replacing.
# - Replaces destination atomically via temp directory + move.
#
# Usage:
#   scripts/refresh-local-msak-xcframework.sh
#   scripts/refresh-local-msak-xcframework.sh 0.2.0
#   MSAK_DIST_ROOT=/path/to/msak-android/msak-shared/build/local-dist/apple \
#     MSAK_FRAMEWORK_DEST=/path/to/iosApp/Frameworks/MsakShared.xcframework \
#     scripts/refresh-local-msak-xcframework.sh 0.2.0

VERSION="${1:-0.2.0}"

DIST_ROOT="${MSAK_DIST_ROOT:-/Users/jeff/Projects/msak-android/msak-shared/build/local-dist/apple}"
SOURCE_DIR="${DIST_ROOT}/msak-client-kmp/${VERSION}"
ZIP_PATH="${SOURCE_DIR}/MsakShared.xcframework.zip"
SHA_PATH="${SOURCE_DIR}/MsakShared.xcframework.sha256"

DEST_PATH="${MSAK_FRAMEWORK_DEST:-/Users/jeff/Projects/cellwatch-app/iosTestApp/Frameworks/MsakShared.xcframework}"
DEST_PARENT="$(dirname "${DEST_PATH}")"

if [[ ! -f "${ZIP_PATH}" ]]; then
  echo "Missing zip: ${ZIP_PATH}" >&2
  exit 1
fi
if [[ ! -f "${SHA_PATH}" ]]; then
  echo "Missing sha256 file: ${SHA_PATH}" >&2
  exit 1
fi

EXPECTED_SHA="$(awk '{print $1}' "${SHA_PATH}")"
ACTUAL_SHA="$(shasum -a 256 "${ZIP_PATH}" | awk '{print $1}')"
if [[ "${EXPECTED_SHA}" != "${ACTUAL_SHA}" ]]; then
  echo "SHA mismatch for ${ZIP_PATH}" >&2
  echo "expected=${EXPECTED_SHA}" >&2
  echo "actual=${ACTUAL_SHA}" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

unzip -q "${ZIP_PATH}" -d "${TMP_DIR}"

EXTRACTED_DIR="${TMP_DIR}/MsakShared.xcframework"
if [[ ! -d "${EXTRACTED_DIR}" ]]; then
  echo "Expected extracted directory missing: ${EXTRACTED_DIR}" >&2
  exit 1
fi

mkdir -p "${DEST_PARENT}"
STAGED_DEST="${DEST_PARENT}/.MsakShared.xcframework.new"
rm -rf "${STAGED_DEST}"
mv "${EXTRACTED_DIR}" "${STAGED_DEST}"
rm -rf "${DEST_PATH}"
mv "${STAGED_DEST}" "${DEST_PATH}"

echo "Installed ${DEST_PATH} from ${ZIP_PATH}"
