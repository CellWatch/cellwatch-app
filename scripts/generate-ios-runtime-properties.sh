#!/usr/bin/env bash
set -euo pipefail

# Generates the runtime configuration resource packaged inside the iOS app bundle.
#
# This is the ONLY runtime configuration source that works on a real device. The
# developer machine's cellwatch.properties files are build-time inputs to this
# script; an installed app cannot read them. Anything the app needs at runtime
# must be written here or it will be missing on device and TestFlight.
#
# The script also decides the EFFECTIVE runtime profile, which is not always the
# one the Xcode configuration asked for: a device cannot reach services bound to
# the developer machine's loopback, so a device build falls back to hosted
# services unless it explicitly opts in to local ones.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="${1:?Usage: generate-ios-runtime-properties.sh <output-file>}"

upper() { printf '%s' "${1:-}" | tr '[:lower:]' '[:upper:]'; }

# Trim surrounding whitespace. Used so a value that is present but blank (an
# empty or whitespace-only env var, e.g. an unsubstituted build setting) is
# treated as missing rather than packaged as a blank URL or key.
trim() {
  local v="${1:-}"
  v="${v#"${v%%[![:space:]]*}"}"
  v="${v%"${v##*[![:space:]]}"}"
  printf '%s' "$v"
}

is_truthy() {
  case "$(upper "${1:-}")" in
    1|TRUE|YES|Y) return 0 ;;
    *) return 1 ;;
  esac
}

# ---------------------------------------------------------------------------
# Build target
#
# Xcode exports every build setting into a script phase's environment, and
# compile-shared-framework-for-xcode.sh invokes this script as a plain child
# process, so PLATFORM_NAME and CONFIGURATION are inherited here.
# ---------------------------------------------------------------------------
PLATFORM="${PLATFORM_NAME:-iphonesimulator}"
if [[ "$PLATFORM" == "iphoneos" ]]; then
  BUILD_TARGET="device"
else
  BUILD_TARGET="simulator"
fi
CONFIGURATION_NAME="${CONFIGURATION:-Debug}"

REQUESTED_SUPABASE_MODE="$(upper "${CELLWATCH_DEFAULT_SUPABASE_MODE:-LOCAL}")"
REQUESTED_MSAK_MODE="$(upper "${CELLWATCH_DEFAULT_MSAK_MODE:-LOCAL}")"

PROPERTY_FILES=(
  "$REPO_ROOT/cellwatch.local.properties"
  "$REPO_ROOT/cellwatch.properties"
  "$REPO_ROOT/iosTestApp/cellwatch.local.properties"
  "$REPO_ROOT/iosTestApp/cellwatch.properties"
)

read_property_value() {
  local key="$1"
  local file="$2"
  [[ -f "$file" ]] || return 1
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue
    if [[ "$line" == "$key="* ]]; then
      local value="${line#*=}"
      value="${value%"${value##*[![:space:]]}"}"
      value="${value#"${value%%[![:space:]]*}"}"
      if [[ "${value:0:1}" == "\"" && "${value: -1}" == "\"" && ${#value} -ge 2 ]]; then
        value="${value:1:${#value}-2}"
      fi
      printf '%s' "$value"
      return 0
    fi
  done < "$file"
  return 1
}

resolve_value() {
  local key="$1"
  local env_value
  env_value="$(trim "${!key:-}")"
  if [[ -n "$env_value" ]]; then
    printf '%s' "$env_value"
    return 0
  fi
  local file
  local value
  for file in "${PROPERTY_FILES[@]}"; do
    value="$(trim "$(read_property_value "$key" "$file" || true)")"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

# Resolves $1 into the global REQUIRED_VALUE, aborting the build when missing.
#
# This deliberately returns through a global rather than stdout. `exit` inside a
# command substitution only leaves the subshell, so the previous
# `write_property "$k" "$(require_value "$k")"` form printed its error and then
# carried on and packaged an empty value - the "fails when a required value is
# missing" guarantee never actually held. Call this as a statement.
REQUIRED_VALUE=""
require_into() {
  local key="$1"
  REQUIRED_VALUE="$(resolve_value "$key" || true)"
  if [[ -z "$REQUIRED_VALUE" ]]; then
    echo "Missing $key for iOS $BUILD_TARGET build (MSAK=$MSAK_MODE, Supabase=$SUPABASE_MODE)." >&2
    exit 1
  fi
}

# Resolve a required key and write it straight through, unchanged.
write_required() {
  local key="$1"
  require_into "$key"
  write_property "$key" "$REQUIRED_VALUE"
}

# ---------------------------------------------------------------------------
# Effective runtime profile
#
# A device cannot reach 127.0.0.1 on the developer machine - on the phone that
# address is the phone. Rather than package an address that cannot work, a
# device build uses hosted services by default. Set CELLWATCH_DEVICE_USE_LOCAL=1
# to aim a device at this machine instead; the LAN address is resolved below.
# ---------------------------------------------------------------------------
SUPABASE_MODE="$REQUESTED_SUPABASE_MODE"
MSAK_MODE="$REQUESTED_MSAK_MODE"
DEVICE_USE_LOCAL=0

if [[ "$BUILD_TARGET" == "device" ]] && is_truthy "${CELLWATCH_DEVICE_USE_LOCAL:-}"; then
  DEVICE_USE_LOCAL=1
fi

if [[ "$BUILD_TARGET" == "device" && "$DEVICE_USE_LOCAL" == "0" ]]; then
  if [[ "$MSAK_MODE" == "LOCAL" ]]; then
    MSAK_MODE="PUBLIC"
    echo "note: device build - MSAK LOCAL is unreachable from a phone, using PUBLIC." >&2
  fi
  if [[ "$SUPABASE_MODE" == "LOCAL" ]]; then
    SUPABASE_MODE="TESTING"
    echo "note: device build - Supabase LOCAL is unreachable from a phone, using TESTING." >&2
  fi
  echo "note: set CELLWATCH_DEVICE_USE_LOCAL=1 to target this machine's local services instead." >&2
fi

# Remote Supabase targets require the allow-remote gate. Deriving it from the
# effective mode removes the possibility of a mode/gate pair that disagree.
case "$SUPABASE_MODE" in
  TESTING|LIVE) ALLOW_REMOTE_SUPABASE="true" ;;
  *) ALLOW_REMOTE_SUPABASE="false" ;;
esac

LAN_IP=""
resolve_lan_ip() {
  local iface ip
  for iface in en0 en1 en2; do
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return 0
    fi
  done
  return 1
}

if [[ "$DEVICE_USE_LOCAL" == "1" ]]; then
  LAN_IP="$(resolve_lan_ip || true)"
  if [[ -z "$LAN_IP" ]]; then
    echo "CELLWATCH_DEVICE_USE_LOCAL=1 but no LAN address found on en0/en1/en2." >&2
    echo "A device needs this machine's LAN address to reach its local services." >&2
    exit 1
  fi
  echo "note: device build targeting local services at $LAN_IP" >&2
fi

# Rewrite a local-service address so it is correct for THIS build target, so the
# packaged value is directly usable and the packaged file states the truth:
#
#   simulator      -> loopback (the simulator shares this machine's network stack)
#   device + local -> this machine's LAN address
#
# Any host that is neither loopback nor the Android emulator alias is passed
# through untouched: a LAN address or real hostname is already correct.
LOCAL_ALIASES='127.0.0.1|localhost|::1|10.0.2.2|10.0.3.2'

target_local_address() {
  if [[ -n "$LAN_IP" ]]; then
    printf '%s' "$LAN_IP"
  else
    printf '127.0.0.1'
  fi
}

target_host() {
  local raw="$1"
  local host_part="${raw%%:*}"
  local port_part=""
  [[ "$raw" == *:* ]] && port_part=":${raw#*:}"
  case "$host_part" in
    127.0.0.1|localhost|::1|10.0.2.2|10.0.3.2)
      printf '%s%s' "$(target_local_address)" "$port_part" ;;
    *)
      printf '%s' "$raw" ;;
  esac
}

target_url() {
  local raw="$1"
  printf '%s' "$raw" | sed -E "s#//(${LOCAL_ALIASES})#//$(target_local_address)#"
}

assert_device_reachable() {
  local key="$1"
  local value="$2"
  if [[ "$BUILD_TARGET" != "device" ]]; then
    return 0
  fi
  case "$value" in
    *127.0.0.1*|*localhost*|*10.0.2.2*|*10.0.3.2*|*::1*)
      echo "Refusing to package $key=$value in a device build: a phone cannot reach it." >&2
      exit 1 ;;
  esac
}

write_property() {
  local key="$1"
  local value="$2"
  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    echo "Refusing multiline value for $key." >&2
    exit 1
  fi
  printf '%s=%s\n' "$key" "$value" >> "$TEMP_FILE"
}

mkdir -p "$(dirname "$OUTPUT_FILE")"
TEMP_FILE="$(mktemp "${OUTPUT_FILE}.tmp.XXXXXX")"
trap 'rm -f "$TEMP_FILE"' EXIT

# --- what was packaged, for assertions -------------------------------------
write_property "CELLWATCH_PACKAGED_TARGET" "$BUILD_TARGET"
write_property "CELLWATCH_PACKAGED_SUPABASE_MODE" "$SUPABASE_MODE"
write_property "CELLWATCH_PACKAGED_MSAK_MODE" "$MSAK_MODE"

# --- the effective profile the app should resolve --------------------------
write_property "CELLWATCH_DEFAULT_SUPABASE_MODE" "$SUPABASE_MODE"
write_property "CELLWATCH_DEFAULT_MSAK_MODE" "$MSAK_MODE"
write_property "CELLWATCH_ALLOW_REMOTE_SUPABASE" "$ALLOW_REMOTE_SUPABASE"

# --- Mapbox ----------------------------------------------------------------
# NOTE: the MAPBOX_DOWNLOADS_TOKEN fallback packages a SECRET ("sk.") token into
# the app bundle. Deliberately left as-is for now; see
# doc/PRE_DEPLOYMENT_CHECKLIST.md item 1, which must be resolved before release.
MAPBOX_TOKEN="$(resolve_value "MAPBOX_ACCESS_TOKEN" || true)"
if [[ -z "$MAPBOX_TOKEN" ]]; then
  MAPBOX_TOKEN="$(resolve_value "MAPBOX_DOWNLOADS_TOKEN" || true)"
fi
if [[ -n "$MAPBOX_TOKEN" ]]; then
  write_property "MAPBOX_ACCESS_TOKEN" "$MAPBOX_TOKEN"
fi

# --- MSAK ------------------------------------------------------------------
case "$MSAK_MODE" in
  LOCAL)
    require_into "MSAK_LOCAL_SERVER_HOST"
    MSAK_HOST="$(target_host "$REQUIRED_VALUE")"
    assert_device_reachable "MSAK_LOCAL_SERVER_HOST" "$MSAK_HOST"
    write_property "MSAK_LOCAL_SERVER_HOST" "$MSAK_HOST"
    MSAK_SECURE="$(resolve_value "MSAK_LOCAL_SERVER_SECURE" || true)"
    write_property "MSAK_LOCAL_SERVER_SECURE" "${MSAK_SECURE:-false}"
    ;;
  PUBLIC|STAGING)
    ;;
  *)
    echo "Unsupported MSAK mode: $MSAK_MODE" >&2
    exit 1 ;;
esac

# --- Supabase --------------------------------------------------------------
case "$SUPABASE_MODE" in
  LOCAL)
    require_into "SUPABASE_LOCAL_URL"
    SUPABASE_LOCAL_URL_VALUE="$(target_url "$REQUIRED_VALUE")"
    assert_device_reachable "SUPABASE_LOCAL_URL" "$SUPABASE_LOCAL_URL_VALUE"
    write_property "SUPABASE_LOCAL_URL" "$SUPABASE_LOCAL_URL_VALUE"
    write_required "SUPABASE_LOCAL_API_KEY"
    # The service-role key is needed for local phase-3 sync but must never reach a
    # non-Debug artifact. LOCAL only occurs in Debug today; the guard makes that
    # structural rather than incidental.
    if [[ "$CONFIGURATION_NAME" == "Debug" ]]; then
      SERVICE_KEY="$(resolve_value "SUPABASE_LOCAL_SERVICE_KEY" || true)"
      if [[ -n "$SERVICE_KEY" ]]; then
        write_property "SUPABASE_LOCAL_SERVICE_KEY" "$SERVICE_KEY"
      fi
    fi
    ;;
  TESTING)
    write_required "SUPABASE_TESTING_URL"
    write_required "SUPABASE_TESTING_API_KEY"
    ;;
  LIVE)
    # LIVE writes to real, deployed data collection, so it needs its own opt-in
    # rather than riding on the allow-remote flag that hosted testing also sets.
    if ! is_truthy "${CELLWATCH_ALLOW_LIVE_SUPABASE:-}"; then
      echo "Supabase mode LIVE requires CELLWATCH_ALLOW_LIVE_SUPABASE=YES." >&2
      echo "LIVE targets deployed production data; set it deliberately." >&2
      exit 1
    fi
    write_property "CELLWATCH_ALLOW_LIVE_SUPABASE" "true"
    write_required "SUPABASE_URL"
    write_required "SUPABASE_API_KEY"
    ;;
  *)
    echo "Unsupported CELLWATCH_DEFAULT_SUPABASE_MODE: $SUPABASE_MODE" >&2
    exit 1 ;;
esac

chmod 600 "$TEMP_FILE"
mv "$TEMP_FILE" "$OUTPUT_FILE"
trap - EXIT
echo "Generated iOS runtime properties: target=$BUILD_TARGET MSAK=$MSAK_MODE Supabase=$SUPABASE_MODE."
