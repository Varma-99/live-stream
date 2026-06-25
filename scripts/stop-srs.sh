#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

cd "${ROOT}"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  for compose in "${SRS_COMPOSE_FILE}" "${SRS_CLUSTER_COMPOSE_FILE}" "${SRS_2TIER_COMPOSE_FILE}"; do
    if [[ -f "${ROOT}/${compose}" ]]; then
      srs_compose -f "${ROOT}/${compose}" down --remove-orphans 2>/dev/null || true
    fi
  done
  for name in "${SRS_CONTAINER_NAME}" "${SRS_ORIGIN_CONTAINER}" srs-edge-1 srs-edge-2 \
    "${SRS_LB_CONTAINER}" "${SRS_WHEP_LB_CONTAINER}" "${SRS_PLAYBACK_CONTAINER}"; do
    if docker ps -a --format '{{.Names}}' | grep -qx "${name}"; then
      docker rm -f "${name}" >/dev/null 2>&1 || true
    fi
  done
  srs_remove_legacy_spike_container
else
  echo "Docker not reachable — skipping compose down."
fi

echo "SRS stopped."
