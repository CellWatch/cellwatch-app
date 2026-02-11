#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

# Prefer project-pinned Gradle JVM for deterministic Xcode builds.
JAVA_HOME_FROM_GRADLE="$(sed -n 's/^org\.gradle\.java\.home=//p' "$REPO_ROOT/gradle.properties" | tail -n1 || true)"
if [[ -n "$JAVA_HOME_FROM_GRADLE" ]]; then
  export JAVA_HOME="$JAVA_HOME_FROM_GRADLE"
elif [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME_FROM_MACOS="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  if [[ -n "$JAVA_HOME_FROM_MACOS" ]]; then
    export JAVA_HOME="$JAVA_HOME_FROM_MACOS"
  fi
fi

if [[ "${CONFIGURATION:-Debug}" == "Debug" ]]; then
  CFG_CAP="Debug"
  CFG_LOW="debug"
else
  CFG_CAP="Release"
  CFG_LOW="release"
fi

PLATFORM="${PLATFORM_NAME:-iphonesimulator}"
ARCHS_VAL="${ARCHS:-}"

if [[ "$PLATFORM" == "iphoneos" ]]; then
  TARGET_SUFFIX="IosArm64"
  TARGET_DIR="iosArm64"
elif [[ "$ARCHS_VAL" == *"x86_64"* ]]; then
  TARGET_SUFFIX="IosX64"
  TARGET_DIR="iosX64"
else
  TARGET_SUFFIX="IosSimulatorArm64"
  TARGET_DIR="iosSimulatorArm64"
fi

TASK_NAME=":shared:link${CFG_CAP}Framework${TARGET_SUFFIX}"
echo "Building shared framework via Gradle task: $TASK_NAME"

args=(./gradlew --no-daemon)
if [[ -n "${CELLWATCH_GRADLE_USER_HOME:-}" ]]; then
  mkdir -p "${CELLWATCH_GRADLE_USER_HOME}"
  args+=("--gradle-user-home" "${CELLWATCH_GRADLE_USER_HOME}")
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  args+=("-Dorg.gradle.java.home=$JAVA_HOME")
fi

case "${CELLWATCH_USE_LOCAL_MSAK_COMPOSITE:-}" in
  1|true|TRUE|yes|YES)
    args+=("-Pcellwatch.useLocalMsak=true")
    if [[ -n "${CELLWATCH_LOCAL_MSAK_DIR:-}" ]]; then
      args+=("-Pcellwatch.local.msak.dir=${CELLWATCH_LOCAL_MSAK_DIR}")
    fi
    ;;
esac

args+=("$TASK_NAME")
"${args[@]}"

SRC_FRAMEWORK="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/${CFG_LOW}Framework/sharedKit.framework"
OUT_FRAMEWORK="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/Current/sharedKit.framework"

if [[ ! -d "$SRC_FRAMEWORK" ]]; then
  echo "Missing Gradle framework output: $SRC_FRAMEWORK" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT_FRAMEWORK")"
rm -rf "$OUT_FRAMEWORK"
cp -R "$SRC_FRAMEWORK" "$OUT_FRAMEWORK"

echo "sharedKit framework ready at: $OUT_FRAMEWORK"
