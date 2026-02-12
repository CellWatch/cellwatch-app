#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/msak-local.sh <start|stop|status|logs|smoke|rebuild>

Manage a local Dockerized MSAK server for Tier 2 integration testing.

Commands:
  start    Build image if missing, then run container (restart unless-stopped)
  stop     Stop and remove the container
  status   Show container status and mapped ports
  logs     Tail container logs
  smoke    Run a quick local client smoke against ws://localhost:8080
  rebuild  Rebuild image from source repo and restart container

Environment variables:
  CELLWATCH_MSAK_REPO_DIR   Path to msak source repo (default: ../msak)
  CELLWATCH_MSAK_IMAGE      Docker image tag (default: msak-local:dev)
  CELLWATCH_MSAK_CONTAINER  Docker container name (default: msak-local)
  CELLWATCH_MSAK_WS_PORT    Host/container ws tcp port (default: 8080)
  CELLWATCH_MSAK_UDP_PORT   Host/container latency udp port (default: 1053)
  CELLWATCH_MSAK_DATA_DIR   Host data dir bind mount (default: <repo>/data)

Examples:
  ./scripts/msak-local.sh start
  CELLWATCH_MSAK_REPO_DIR=/absolute/path/to/msak ./scripts/msak-local.sh rebuild
USAGE
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MSAK_REPO_DIR="${CELLWATCH_MSAK_REPO_DIR:-$REPO_ROOT/../msak}"
MSAK_IMAGE="${CELLWATCH_MSAK_IMAGE:-msak-local:dev}"
MSAK_CONTAINER="${CELLWATCH_MSAK_CONTAINER:-msak-local}"
MSAK_WS_PORT="${CELLWATCH_MSAK_WS_PORT:-8080}"
MSAK_UDP_PORT="${CELLWATCH_MSAK_UDP_PORT:-1053}"
MSAK_DATA_DIR="${CELLWATCH_MSAK_DATA_DIR:-$MSAK_REPO_DIR/data}"

require_msak_repo() {
  if [[ ! -d "$MSAK_REPO_DIR" ]]; then
    echo "MSAK repo not found: $MSAK_REPO_DIR" >&2
    echo "Set CELLWATCH_MSAK_REPO_DIR to your local msak checkout." >&2
    exit 1
  fi
  if [[ ! -f "$MSAK_REPO_DIR/Dockerfile" ]]; then
    echo "No Dockerfile found under: $MSAK_REPO_DIR" >&2
    echo "Expected a checkout of https://github.com/m-lab/msak." >&2
    exit 1
  fi
}

container_exists() {
  docker ps -a --format '{{.Names}}' | grep -Fxq "$MSAK_CONTAINER"
}

ensure_image() {
  if ! docker image inspect "$MSAK_IMAGE" >/dev/null 2>&1; then
    echo "Image $MSAK_IMAGE not found; building from $MSAK_REPO_DIR"
    build_image
  fi
}

build_image() {
  require_msak_repo
  mkdir -p "$MSAK_DATA_DIR"
  docker build -t "$MSAK_IMAGE" "$MSAK_REPO_DIR"
}

start_container() {
  mkdir -p "$MSAK_DATA_DIR"

  if container_exists; then
    local running
    running="$(docker inspect -f '{{.State.Running}}' "$MSAK_CONTAINER")"
    if [[ "$running" == "true" ]]; then
      echo "$MSAK_CONTAINER is already running."
      return 0
    fi
    docker rm "$MSAK_CONTAINER" >/dev/null
  fi

  docker run -d \
    --name "$MSAK_CONTAINER" \
    --restart unless-stopped \
    -p "$MSAK_WS_PORT:$MSAK_WS_PORT" \
    -p "$MSAK_UDP_PORT:$MSAK_UDP_PORT/udp" \
    -v "$MSAK_DATA_DIR:/msak/data" \
    "$MSAK_IMAGE" \
    --ws_addr ":$MSAK_WS_PORT" \
    --latency_addr ":$MSAK_UDP_PORT" \
    --datadir /msak/data >/dev/null

  echo "Started $MSAK_CONTAINER"
  echo "WS endpoint: ws://localhost:$MSAK_WS_PORT"
  echo "UDP endpoint: localhost:$MSAK_UDP_PORT"
}

stop_container() {
  if ! container_exists; then
    echo "$MSAK_CONTAINER is not present."
    return 0
  fi
  docker rm -f "$MSAK_CONTAINER" >/dev/null
  echo "Stopped and removed $MSAK_CONTAINER"
}

status_container() {
  docker ps --filter "name=^/${MSAK_CONTAINER}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

logs_container() {
  docker logs -f "$MSAK_CONTAINER"
}

smoke_client() {
  require_msak_repo
  local client_bin="$MSAK_REPO_DIR/msak-client"

  if [[ ! -x "$client_bin" ]]; then
    echo "Building msak client binary for smoke test..."
    (cd "$MSAK_REPO_DIR" && go build -o msak-client ./cmd/msak-client)
  fi

  "$client_bin" -duration=1s -streams=1 -server "localhost:$MSAK_WS_PORT" -scheme ws
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

require_cmd docker
action="$1"

case "$action" in
  start)
    ensure_image
    start_container
    ;;
  stop)
    stop_container
    ;;
  status)
    status_container
    ;;
  logs)
    logs_container
    ;;
  smoke)
    smoke_client
    ;;
  rebuild)
    stop_container
    build_image
    start_container
    ;;
  *)
    usage
    exit 1
    ;;
esac
