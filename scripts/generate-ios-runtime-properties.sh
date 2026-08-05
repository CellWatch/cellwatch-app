#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="${1:?Usage: generate-ios-runtime-properties.sh <output-file>}"
SUPABASE_MODE="$(printf '%s' "${CELLWATCH_DEFAULT_SUPABASE_MODE:-LOCAL}" | tr '[:lower:]' '[:upper:]')"

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
  local env_value="${!key:-}"
  if [[ -n "$env_value" ]]; then
    printf '%s' "$env_value"
    return 0
  fi
  local file
  local value
  for file in "${PROPERTY_FILES[@]}"; do
    value="$(read_property_value "$key" "$file" || true)"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

require_value() {
  local key="$1"
  local value
  value="$(resolve_value "$key" || true)"
  if [[ -z "$value" ]]; then
    echo "Missing $key for iOS $SUPABASE_MODE runtime packaging." >&2
    exit 1
  fi
  printf '%s' "$value"
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

write_property "CELLWATCH_PACKAGED_SUPABASE_MODE" "$SUPABASE_MODE"

MAPBOX_TOKEN="$(resolve_value "MAPBOX_ACCESS_TOKEN" || true)"
if [[ -z "$MAPBOX_TOKEN" ]]; then
  MAPBOX_TOKEN="$(resolve_value "MAPBOX_DOWNLOADS_TOKEN" || true)"
fi
if [[ -n "$MAPBOX_TOKEN" ]]; then
  write_property "MAPBOX_ACCESS_TOKEN" "$MAPBOX_TOKEN"
fi

case "$SUPABASE_MODE" in
  LOCAL)
    ;;
  TESTING)
    write_property "SUPABASE_TESTING_URL" "$(require_value "SUPABASE_TESTING_URL")"
    write_property "SUPABASE_TESTING_API_KEY" "$(require_value "SUPABASE_TESTING_API_KEY")"
    ;;
  LIVE)
    write_property "SUPABASE_URL" "$(require_value "SUPABASE_URL")"
    write_property "SUPABASE_API_KEY" "$(require_value "SUPABASE_API_KEY")"
    ;;
  *)
    echo "Unsupported CELLWATCH_DEFAULT_SUPABASE_MODE: $SUPABASE_MODE" >&2
    exit 1
    ;;
esac

chmod 600 "$TEMP_FILE"
mv "$TEMP_FILE" "$OUTPUT_FILE"
trap - EXIT
echo "Generated iOS runtime properties for Supabase mode $SUPABASE_MODE."
