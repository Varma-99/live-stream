#!/usr/bin/env bash
# Start SRS via Docker Compose (Colima).
# Single node (default): RTMP :1935, API/WHEP :1985
# Edge cluster: SRS_CLUSTER_MODE=edge — origin :1935/:1995, WHEP LB :1985, edges UDP :8001/:8002
# 2-tier: SRS_STACK=2tier — RTMP origin + playback WHEP LB :1985, playback UDP :8000
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

cd "${ROOT}"
srs_require_docker
srs_require_compose_file "${ROOT}"

COMPOSE_FILE="$(srs_active_compose_file)"
REQUESTED_STACK="$(srs_stack)"

"${ROOT}/scripts/check-colima-webrtc.sh" || true

if srs_any_stack_running; then
  RUNNING_STACK="$(srs_running_stack_name)"
  if [[ "${RUNNING_STACK}" != "${REQUESTED_STACK}" ]]; then
    echo "SRS ${RUNNING_STACK} stack is already running. Stop it first: ./scripts/stop-srs.sh"
    exit 1
  fi
  echo "SRS ${REQUESTED_STACK} stack is already running."
  echo "  LAN IP now: $(srs_lan_ip)"
  echo "  If your IP changed, run: ./scripts/stop-srs.sh && SRS_STACK=${SRS_STACK:-} SRS_CLUSTER_MODE=${SRS_CLUSTER_MODE:-single} ./scripts/start-srs.sh"
  docker ps --filter "name=srs"
  srs_print_endpoints
  exit 0
fi

srs_require_rtmp_port_free
srs_remove_legacy_spike_container

echo "Pulling images (first run may take a minute)…"
srs_compose -f "${COMPOSE_FILE}" pull

srs_export_candidate_env
case "${REQUESTED_STACK}" in
  2tier)
    echo "Starting SRS 2-tier stack (playback CANDIDATE=${SRS_PLAYBACK_CANDIDATE})"
    ;;
  edge)
    echo "Starting SRS edge cluster (CANDIDATE edges: ${SRS_EDGE1_CANDIDATE}, ${SRS_EDGE2_CANDIDATE})"
    ;;
  *)
    echo "Starting SRS single node (CANDIDATE=${SRS_CANDIDATE} for WebRTC ICE — match publicWebBase host in config)"
    ;;
esac
srs_compose -f "${COMPOSE_FILE}" up -d

sleep 4
case "${REQUESTED_STACK}" in
  2tier)
    if srs_2tier_running; then
      echo "SRS 2-tier stack is up."
      srs_print_endpoints
    else
      echo "SRS 2-tier stack failed to start. Logs:"
      srs_compose -f "${COMPOSE_FILE}" logs --tail 50
      exit 1
    fi
    ;;
  edge)
    if srs_cluster_running; then
      echo "SRS edge cluster is up."
      srs_print_endpoints
    else
      echo "SRS edge cluster failed to start. Logs:"
      srs_compose -f "${COMPOSE_FILE}" logs --tail 50
      exit 1
    fi
    ;;
  *)
    if srs_single_running; then
      echo "SRS is up."
      docker logs --tail 20 "${SRS_CONTAINER_NAME}" 2>/dev/null || true
      srs_print_endpoints
    else
      echo "SRS failed to start. Logs:"
      srs_compose -f "${COMPOSE_FILE}" logs --tail 50
      exit 1
    fi
    ;;
esac
