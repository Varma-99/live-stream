#!/usr/bin/env bash
# Shared helpers for SRS Docker scripts.
set -euo pipefail

SRS_COMPOSE_FILE="docker-compose.srs.yml"
SRS_CLUSTER_COMPOSE_FILE="docker-compose.srs-cluster.yml"
SRS_2TIER_COMPOSE_FILE="docker-compose.srs-2tier.yml"
SRS_CONTAINER_NAME="srs"
SRS_ORIGIN_CONTAINER="srs-origin"
SRS_LB_CONTAINER="srs-lb"
SRS_WHEP_LB_CONTAINER="srs-whep-lb"
SRS_PLAYBACK_CONTAINER="srs-playback-1"
SRS_LEGACY_CONTAINER="srs-spike"
SRS_RTMP_PORT=1935

# Stack: single (default) | edge (SRS_CLUSTER_MODE=edge) | 2tier (SRS_STACK=2tier)
srs_stack() {
  case "${SRS_STACK:-}" in
    2tier) echo "2tier" ;;
    *)
      if [[ "${SRS_CLUSTER_MODE:-single}" == "edge" ]]; then
        echo "edge"
      else
        echo "single"
      fi
      ;;
  esac
}

# single | edge — override with SRS_CLUSTER_MODE=edge (legacy alias)
srs_cluster_mode() {
  echo "$(srs_stack)"
}

srs_is_2tier_stack() {
  [[ "$(srs_stack)" == "2tier" ]]
}

srs_is_edge_cluster() {
  [[ "$(srs_stack)" == "edge" ]]
}

srs_active_compose_file() {
  case "$(srs_stack)" in
    2tier) echo "${SRS_2TIER_COMPOSE_FILE}" ;;
    edge) echo "${SRS_CLUSTER_COMPOSE_FILE}" ;;
    *) echo "${SRS_COMPOSE_FILE}" ;;
  esac
}

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

docker_container_status() {
  local name="$1"
  docker inspect -f '{{.State.Status}}' "${name}" 2>/dev/null || echo "missing"
}

docker_container_running() {
  [[ "$(docker_container_status "$1")" == "running" ]]
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
  echo "  colima start --port-forwarder=grpc"
  echo "  docker info"
  exit 1
}

srs_require_compose_file() {
  local root="$1"
  local compose
  compose="$(srs_active_compose_file)"
  if [[ ! -f "${root}/${compose}" ]]; then
    echo "Missing ${root}/${compose}"
    exit 1
  fi
  if srs_is_2tier_stack; then
    for f in srs.origin.rtmp.conf srs.playback.conf haproxy-srs.cfg; do
      if [[ ! -f "${root}/${f}" ]]; then
        echo "Missing ${root}/${f} (required for 2-tier stack)"
        exit 1
      fi
    done
  elif srs_is_edge_cluster; then
    for f in srs.origin.conf srs.edge.conf haproxy.cfg; do
      if [[ ! -f "${root}/${f}" ]]; then
        echo "Missing ${root}/${f} (required for edge cluster)"
        exit 1
      fi
    done
  elif [[ ! -f "${root}/srs.conf" ]]; then
    echo "Missing ${root}/srs.conf"
    exit 1
  fi
}

srs_cluster_running() {
  curl -sf --max-time 3 "http://127.0.0.1:1995/api/v1/streams/" >/dev/null \
    && curl -sf --max-time 3 "http://127.0.0.1:1985/api/v1/streams/" >/dev/null \
    && docker_container_running "${SRS_LB_CONTAINER}"
}

srs_2tier_running() {
  curl -sf --max-time 3 "http://127.0.0.1:1995/api/v1/streams/" >/dev/null \
    && curl -sf --max-time 3 "http://127.0.0.1:1985/api/v1/streams/" >/dev/null \
    && docker_container_running "${SRS_WHEP_LB_CONTAINER}" \
    && docker_container_running "${SRS_PLAYBACK_CONTAINER}"
}

srs_single_running() {
  docker_container_running "${SRS_CONTAINER_NAME}"
}

srs_any_stack_running() {
  srs_single_running || srs_cluster_running || srs_2tier_running
}

srs_running_stack_name() {
  if srs_2tier_running; then
    echo "2tier"
  elif srs_cluster_running; then
    echo "edge"
  elif srs_single_running; then
    echo "single"
  else
    echo "none"
  fi
}

srs_any_running() {
  srs_any_stack_running
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
  else
    echo "Stop the RTMP server using that port, or:"
    echo "  ./scripts/stop-srs.sh"
  fi
  exit 1
}

srs_remove_legacy_spike_container() {
  if docker ps -a --format '{{.Names}}' | grep -qx "${SRS_LEGACY_CONTAINER}"; then
    echo "Removing legacy Phase 1 container ${SRS_LEGACY_CONTAINER}…"
    docker rm -f "${SRS_LEGACY_CONTAINER}" >/dev/null 2>&1 || true
  fi
}

srs_lan_ip() {
  ipconfig getifaddr en0 2>/dev/null || echo "10.255.61.28"
}

srs_export_candidate_env() {
  local lan
  lan="$(srs_lan_ip)"
  if [[ -z "${SRS_CANDIDATE:-}" ]]; then
    export SRS_CANDIDATE="${lan}"
  fi
  if srs_is_2tier_stack; then
    export SRS_PLAYBACK_CANDIDATE="${SRS_PLAYBACK_CANDIDATE:-${lan}:8000}"
  elif srs_is_edge_cluster; then
    export SRS_ORIGIN_CANDIDATE="${SRS_ORIGIN_CANDIDATE:-${lan}:8000}"
    export SRS_EDGE1_CANDIDATE="${SRS_EDGE1_CANDIDATE:-${lan}:8001}"
    export SRS_EDGE2_CANDIDATE="${SRS_EDGE2_CANDIDATE:-${lan}:8002}"
  fi
}

srs_print_endpoints() {
  local stream_key="${1:-phase1test}"
  if srs_is_2tier_stack; then
    srs_print_2tier_endpoints "${stream_key}"
  elif srs_is_edge_cluster; then
    srs_print_cluster_endpoints "${stream_key}"
  else
    echo "  RTMP ingest:  rtmp://127.0.0.1:1935/live/${stream_key}"
    echo "  HTTP API:     http://127.0.0.1:1985/api/v1/streams/"
    echo "  WHEP play:    http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=${stream_key}"
    echo "  FLV player:   http://127.0.0.1:8088/players/srs_player.html?stream=${stream_key}.flv&port=8088"
    echo "  HLS play:     http://127.0.0.1:8088/live/${stream_key}.m3u8"
    echo "  Test publish: ./scripts/publish-srs-test.sh ${stream_key}"
    echo "  Stop:         ./scripts/stop-srs.sh"
  fi
}

srs_print_2tier_endpoints() {
  local stream_key="${1:-phase1test}"
  local lan
  lan="$(srs_lan_ip)"
  echo "  Stack:        2-tier (RTMP origin → forward → playback WHEP)"
  echo "  RTMP ingest:  rtmp://127.0.0.1:1935/live/${stream_key}  (origin)"
  echo "  Ingest API:   http://127.0.0.1:1995/api/v1/streams/  (origin — app srsApiBase)"
  echo "  WHEP LB:      http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=${stream_key}"
  echo "  WebRTC UDP:   ${lan}:8000 (playback tier)"
  echo "  Gate test:    ./scripts/test-2tier-whep.sh ${stream_key}"
  echo "  App config:   config/config-dev-2tier.yml"
  echo "  Stop:         ./scripts/stop-srs.sh"
}

srs_print_cluster_endpoints() {
  local stream_key="${1:-phase1test}"
  local lan
  lan="$(srs_lan_ip)"
  echo "  Cluster mode: edge (origin WHEP + RTMP ingest; SRS 6 edges are RTMP-only)"
  echo "  RTMP ingest:  rtmp://127.0.0.1:1935/live/${stream_key}  (origin)"
  echo "  Ingest API:   http://127.0.0.1:1995/api/v1/streams/  (origin — app srsApiBase)"
  echo "  WHEP LB:      http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=${stream_key}"
  echo "  WebRTC UDP:   ${lan}:8000 (origin — viewers use WHEP on origin via LB)"
  echo "  Edge UDP:     ${lan}:8001 / :8002 (reserved; RTC disabled on SRS RTMP edges)"
  echo "  App config:   ./mvnw -Dexec.args=\"server config/config-dev-cluster.yml\" compile exec:java"
  echo "  Stop:         ./scripts/stop-srs.sh"
}
