#!/usr/bin/env bash
set -euo pipefail
trap 'echo "ERROR: $0 failed at line $LINENO" >&2' ERR

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

cleanup_lock() {
  rm -rf "${LOCK_DIR:-}"
}

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
FORCE_RERUN="${CELLWATCH_XCODE_FORCE_RERUN:-1}"
AUTO_RETRY="${CELLWATCH_XCODE_AUTO_RETRY:-1}"

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

LINK_TASK=":shared:link${CFG_CAP}Framework${TARGET_SUFFIX}"
REFRESH_TASK=""
if [[ "$TARGET_DIR" == "iosSimulatorArm64" && "$CFG_CAP" == "Debug" ]]; then
  # Keep Xcode path aligned with Gradle-hosted simulator checks/tasks.
  REFRESH_TASK=":shared:refreshIosSimulatorCurrentFramework"
fi
TASK_NAME="${REFRESH_TASK:-$LINK_TASK}"
echo "Building shared framework via Gradle task: $TASK_NAME (force_rerun=$FORCE_RERUN)"

LOCK_DIR="${TMPDIR:-/tmp}/cellwatch-shared-framework.lockdir"
LOCK_PID_FILE="$LOCK_DIR/pid"
LOCK_TIMEOUT_SECONDS="${CELLWATCH_XCODE_LOCK_TIMEOUT_SECONDS:-120}"
lock_waited=0
while ! mkdir "$LOCK_DIR" 2>/dev/null; do
  stale_lock=0
  if [[ -f "$LOCK_PID_FILE" ]]; then
    lock_pid="$(cat "$LOCK_PID_FILE" 2>/dev/null || true)"
    if [[ -n "${lock_pid:-}" ]] && ! kill -0 "$lock_pid" 2>/dev/null; then
      stale_lock=1
    fi
  else
    # Lock exists but has no owner metadata; consider it stale after timeout window.
    stale_lock=1
  fi
  if [[ "$stale_lock" == "1" ]]; then
    rm -rf "$LOCK_DIR"
    continue
  fi
  if [[ "$lock_waited" -ge "$LOCK_TIMEOUT_SECONDS" ]]; then
    echo "Timed out waiting for shared framework build lock: $LOCK_DIR" >&2
    exit 1
  fi
  sleep 1
  lock_waited=$((lock_waited + 1))
done
echo "$$" > "$LOCK_PID_FILE"
trap cleanup_lock EXIT

if [[ -z "${CELLWATCH_GRADLE_USER_HOME:-}" ]]; then
  CELLWATCH_GRADLE_USER_HOME="$REPO_ROOT/.gradle-xcode"
fi

build_gradle() {
  local force_rerun_arg="$1"
  local args=(./gradlew --no-daemon)
  mkdir -p "${CELLWATCH_GRADLE_USER_HOME}"
  args+=("--gradle-user-home" "${CELLWATCH_GRADLE_USER_HOME}")
  if [[ -n "${JAVA_HOME:-}" ]]; then
    args+=("-Dorg.gradle.java.home=$JAVA_HOME")
  fi

  # Optional composite-source mode for Xcode-driven iOS builds.
  # Default behavior should remain Maven artifact resolution.
  # Enable only when you need direct source substitution from a local msak checkout.
  case "${CELLWATCH_USE_LOCAL_MSAK_COMPOSITE:-}" in
    1|true|TRUE|yes|YES)
      args+=("-Pcellwatch.useLocalMsak=true")
      if [[ -n "${CELLWATCH_LOCAL_MSAK_DIR:-}" ]]; then
        args+=("-Pcellwatch.local.msak.dir=${CELLWATCH_LOCAL_MSAK_DIR}")
      fi
      ;;
  esac

  case "$force_rerun_arg" in
    1|true|TRUE|yes|YES)
      args+=("--rerun-tasks")
      ;;
  esac

  args+=("$TASK_NAME")
  "${args[@]}"
}

SRC_FRAMEWORK="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/${CFG_LOW}Framework/sharedKit.framework"
SRC_DSYM="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/${CFG_LOW}Framework/sharedKit.framework.dSYM"
TARGET_CURRENT_FRAMEWORK="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/Current/sharedKit.framework"
TARGET_CURRENT_DSYM="$REPO_ROOT/shared/build/bin/${TARGET_DIR}/Current/sharedKit.framework.dSYM"
OUT_FRAMEWORK="$REPO_ROOT/shared/build/bin/Current/sharedKit.framework"
OUT_DSYM="$REPO_ROOT/shared/build/bin/Current/sharedKit.framework.dSYM"
OUT_BINARY="$OUT_FRAMEWORK/sharedKit"

copy_dir_if_present() {
  local src="$1"
  local dest="$2"
  if [[ ! -e "$src" ]]; then
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  rm -rf "$dest"
  cp -R "$src" "$dest"
}

prepare_target_dirs() {
  rm -rf "$TARGET_CURRENT_FRAMEWORK"
  rm -rf "$TARGET_CURRENT_DSYM"
  rm -rf "$REPO_ROOT/shared/build/bin/${TARGET_DIR}/${CFG_LOW}Framework/sharedKit.framework"
  rm -rf "$REPO_ROOT/shared/build/bin/${TARGET_DIR}/${CFG_LOW}Framework/sharedKit.framework.dSYM"
  rm -rf "$OUT_FRAMEWORK"
  rm -rf "$OUT_DSYM"
}

verify_fresh_output() {
  if [[ ! -f "$OUT_BINARY" ]]; then
    echo "Missing framework binary at: $OUT_BINARY" >&2
    return 1
  fi

  latest_input_epoch="$(
    find "$REPO_ROOT/shared/src" \
         "$REPO_ROOT/shared/build.gradle.kts" \
         "$REPO_ROOT/gradle/libs.versions.toml" \
         -type f -print0 2>/dev/null \
      | xargs -0 stat -f "%m" \
      | sort -nr \
      | head -n1
  )"
  binary_epoch="$(stat -f "%m" "$OUT_BINARY")"
  if [[ -n "${latest_input_epoch:-}" && "$binary_epoch" -lt "$latest_input_epoch" ]]; then
    echo "Framework appears stale: $OUT_BINARY (binary=$binary_epoch, latestInput=$latest_input_epoch)" >&2
    return 1
  fi
  return 0
}

attempt_build() {
  local force_rerun_arg="$1"
  build_gradle "$force_rerun_arg" || return 1

  if [[ -n "$REFRESH_TASK" ]]; then
    # refreshIosSimulatorCurrentFramework already updates Current output.
    if [[ ! -d "$TARGET_CURRENT_FRAMEWORK" ]]; then
      echo "Missing refreshed framework output: $TARGET_CURRENT_FRAMEWORK" >&2
      return 1
    fi
  else
    if [[ ! -d "$SRC_FRAMEWORK" ]]; then
      echo "Missing Gradle framework output: $SRC_FRAMEWORK" >&2
      return 1
    fi

    mkdir -p "$(dirname "$TARGET_CURRENT_FRAMEWORK")"
    rm -rf "$TARGET_CURRENT_FRAMEWORK"
    cp -R "$SRC_FRAMEWORK" "$TARGET_CURRENT_FRAMEWORK"
    copy_dir_if_present "$SRC_DSYM" "$TARGET_CURRENT_DSYM"
  fi

  mkdir -p "$(dirname "$OUT_FRAMEWORK")"
  rm -rf "$OUT_FRAMEWORK"
  cp -R "$TARGET_CURRENT_FRAMEWORK" "$OUT_FRAMEWORK"
  copy_dir_if_present "$TARGET_CURRENT_DSYM" "$OUT_DSYM"

  verify_fresh_output
}

if ! attempt_build "$FORCE_RERUN"; then
  case "$AUTO_RETRY" in
    1|true|TRUE|yes|YES)
      echo "Initial shared framework build failed; retrying once with cleaned target outputs..." >&2
      prepare_target_dirs
      attempt_build "1"
      ;;
    *)
      exit 1
      ;;
  esac
fi

generate_runtime_properties_resource() {
  if [[ -z "${BUILT_PRODUCTS_DIR:-}" || -z "${UNLOCALIZED_RESOURCES_FOLDER_PATH:-}" ]]; then
    # The packaged resource is the only runtime configuration an installed app
    # can read, so skipping generation produces an app that silently has no
    # config. Inside Xcode that is a build error; outside it (a plain CLI or
    # Gradle-driven framework build) there is no bundle to write into, so say so
    # and carry on rather than failing a build that never needed the resource.
    if [[ -n "${XCODE_VERSION_ACTUAL:-}" ]]; then
      echo "BUILT_PRODUCTS_DIR/UNLOCALIZED_RESOURCES_FOLDER_PATH unset inside an Xcode build;" >&2
      echo "cannot write cellwatch.runtime.properties, which the app needs at runtime." >&2
      exit 1
    fi
    echo "note: no bundle resource path in this environment; skipping runtime properties." >&2
    return 0
  fi
  local out_dir="$BUILT_PRODUCTS_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
  local out_file="$out_dir/cellwatch.runtime.properties"
  "$REPO_ROOT/scripts/generate-ios-runtime-properties.sh" "$out_file"
}

stage_archive_dsyms() {
  if [[ -z "${DWARF_DSYM_FOLDER_PATH:-}" ]]; then
    return 0
  fi

  copy_dir_if_present "$OUT_DSYM" "$DWARF_DSYM_FOLDER_PATH/$(basename "$OUT_DSYM")"

  if [[ -z "${BUILD_DIR:-}" ]]; then
    return 0
  fi

  local derived_data_dir
  derived_data_dir="$(cd "$BUILD_DIR/../.." 2>/dev/null && pwd)"
  local artifacts_dir="$derived_data_dir/SourcePackages/artifacts"
  if [[ ! -d "$artifacts_dir" ]]; then
    return 0
  fi

  local framework_name
  for framework_name in MapboxCommon MapboxCoreMaps Turf; do
    local package_dsym
    package_dsym="$(find "$artifacts_dir" -type d -name "${framework_name}.framework.dSYM" -print -quit 2>/dev/null || true)"
    if [[ -n "$package_dsym" ]]; then
      copy_dir_if_present "$package_dsym" "$DWARF_DSYM_FOLDER_PATH/$(basename "$package_dsym")"
    fi
  done
}

generate_runtime_properties_resource
stage_archive_dsyms

echo "sharedKit framework ready at: $OUT_FRAMEWORK"
