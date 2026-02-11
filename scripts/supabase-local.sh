#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/supabase-local.sh <start|stop|status|reset>

Commands:
  start   Start local Supabase services (Docker-based)
  stop    Stop local Supabase services
  status  Show local Supabase service status
  reset   Reset local DB using current supabase project config
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

action="$1"
require_cmd supabase
require_cmd docker

case "$action" in
  start)
    supabase start
    echo
    echo "Local Supabase started."
    echo "API: http://127.0.0.1:54321"
    echo "DB:  postgresql://postgres:postgres@127.0.0.1:54322/postgres"
    ;;
  stop)
    supabase stop
    ;;
  status)
    supabase status
    ;;
  reset)
    supabase db reset
    ;;
  *)
    usage
    exit 1
    ;;
esac
