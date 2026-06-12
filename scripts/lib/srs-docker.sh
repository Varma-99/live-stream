#!/usr/bin/env bash
# Shared helpers for SRS Docker scripts.
set -euo pipefail

SRS_COMPOSE_FILE="docker-compose.srs.yml"
SRS_CONTAINER_NAME="srs"
SRS_LEGACY_CONTAINER="srs-spike"
SRS_RTMP_PORT=1935

srs_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    echo "Docker Compose not found. Install: brew install docker-compose"
    exit 1
  fi
}

srs_project_root() {
  cd "$(dirname "${BASH_SOURCE[1]}")/.." && pwd
}

srs_require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker CLI not found. Install: brew install docker colima"
    exit 1
  fi
  local attempt
  for attempt in 1 2 3 4 5; do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    if [[ "${attempt}" -lt 5 ]]; then
      echo "Waiting for Docker daemon (attempt ${attempt}/5)…"
      sleep 2
    fi
  done
  echo "Docker daemon not reachable. Try:"
  echo "  colima restart"
  echo "  docker info"
  echo "If you ran stop-mediamtx.sh while SRS was up, Colima may be broken — colima restart fixes it."
  exit 1
}

srs_require_compose_file() {
  local root="$1"
  if [[ ! -f "${root}/${SRS_COMPOSE_FILE}" ]]; then
    echo "Missing ${root}/${SRS_COMPOSE_FILE}"
    exit 1
  fi
  if [[ ! -f "${root}/srs.conf" ]]; then
    echo "Missing ${root}/srs.conf"
    exit 1
  fi
}

srs_port_1935_listener() {
  lsof -nP -iTCP:"${SRS_RTMP_PORT}" -sTCP:LISTEN 2>/dev/null || true
}

srs_require_rtmp_port_free() {
  if pgrep -x mediamtx >/dev/null 2>&1; then
    echo "MediaMTX is running. Stop it first (shares port ${SRS_RTMP_PORT}):"
    echo "  ./scripts/stop-mediamtx.sh"
    exit 1
  fi
  if ! lsof -tiTCP:"${SRS_RTMP_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    return 0
  fi
  local listener
  listener="$(srs_port_1935_listener)"
  echo "Port ${SRS_RTMP_PORT} is in use:"
  echo "${listener}"
  if echo "${listener}" | grep -qiE 'ssh|limactl|colima'; then
    echo "This is Colima/Docker forwarding (SRS may still be bound). Use:"
    echo "  ./scripts/stop-srs.sh"
    echo "Do NOT use stop-mediamtx.sh — it used to kill Colima ssh and break Docker."
  else
    echo "Stop the RTMP server using that port, or:"
    echo "  ./scripts/stop-srs.sh"
    echo "  ./scripts/stop-mediamtx.sh   (only if MediaMTX is the listener)"
  fi
  exit 1
}

srs_remove_legacy_spike_container() {
  if docker ps -a --format '{{.Names}}' | grep -qx "${SRS_LEGACY_CONTAINER}"; then
    echo "Removing legacy Phase 1 container ${SRS_LEGACY_CONTAINER}…"
    docker rm -f "${SRS_LEGACY_CONTAINER}" >/dev/null 2>&1 || true
  fi
}

srs_print_endpoints() {
  local stream_key="${1:-phase1test}"
  echo "  RTMP ingest:  rtmp://127.0.0.1:1935/live/${stream_key}"
  echo "  HTTP API:     http://127.0.0.1:1985/api/v1/streams/"
  echo "  WHEP play:    http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=${stream_key}"
  echo "  FLV player:   http://127.0.0.1:8088/players/srs_player.html?stream=${stream_key}.flv&port=8088"
  echo "  HLS play:     http://127.0.0.1:8088/live/${stream_key}.m3u8"
  echo "  Test publish: ./scripts/publish-srs-test.sh ${stream_key}"
  echo "  Stop:         ./scripts/stop-srs.sh"
}
