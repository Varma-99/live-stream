#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

cd "${ROOT}"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if [[ -f "${SRS_COMPOSE_FILE}" ]]; then
    srs_compose -f "${SRS_COMPOSE_FILE}" down --remove-orphans 2>/dev/null || true
  fi
  if docker ps -a --format '{{.Names}}' | grep -qx "${SRS_CONTAINER_NAME}"; then
    docker rm -f "${SRS_CONTAINER_NAME}" >/dev/null 2>&1 || true
  fi
  srs_remove_legacy_spike_container
else
  echo "Docker not reachable — skipping compose down."
fi

echo "SRS stopped."
