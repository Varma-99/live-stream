#!/usr/bin/env bash
# Start SRS via Docker Compose (Colima) — RTMP :1935, API/WHEP :1985, players :8088
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

cd "${ROOT}"
srs_require_docker
srs_require_compose_file "${ROOT}"

"${ROOT}/scripts/check-colima-webrtc.sh" || true

if docker ps --format '{{.Names}}' | grep -qx "${SRS_CONTAINER_NAME}"; then
  echo "SRS is already running (${SRS_CONTAINER_NAME})."
  echo "  WebRTC CANDIDATE in container: $(docker exec "${SRS_CONTAINER_NAME}" printenv CANDIDATE 2>/dev/null || echo '?')"
  echo "  LAN IP now: $(ipconfig getifaddr en0 2>/dev/null || echo 'unknown')"
  echo "  If your IP changed, run: ./scripts/stop-srs.sh && ./scripts/start-srs.sh"
  docker ps --filter "name=${SRS_CONTAINER_NAME}"
  srs_print_endpoints
  exit 0
fi

srs_require_rtmp_port_free
srs_remove_legacy_spike_container

echo "Pulling ossrs/srs:6 (first run may take a minute)…"
srs_compose -f "${SRS_COMPOSE_FILE}" pull

if [[ -z "${SRS_CANDIDATE:-}" ]]; then
  CANDIDATE="$(ipconfig getifaddr en0 2>/dev/null || true)"
  if [[ -z "${CANDIDATE}" ]]; then
    CANDIDATE="10.255.51.126"
  fi
else
  CANDIDATE="${SRS_CANDIDATE}"
fi
export SRS_CANDIDATE="${CANDIDATE}"
echo "Starting SRS (CANDIDATE=${CANDIDATE} for WebRTC ICE — must match publicWebBase host in config-dev.yml)"
srs_compose -f "${SRS_COMPOSE_FILE}" up -d

sleep 2
if docker ps --format '{{.Names}}' | grep -qx "${SRS_CONTAINER_NAME}"; then
  echo "SRS is up."
  srs_print_endpoints
  docker logs --tail 20 "${SRS_CONTAINER_NAME}"
else
  echo "SRS failed to start. Logs:"
  srs_compose -f "${SRS_COMPOSE_FILE}" logs --tail 50
  exit 1
fi
