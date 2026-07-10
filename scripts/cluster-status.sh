#!/usr/bin/env bash
# Quick health check for SRS edge cluster (origin + edges + HAProxy).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"
# shellcheck source=load-test/lib/srs-cluster.sh
source "${ROOT}/load-test/lib/srs-cluster.sh"

apply_cluster_env

echo "SRS edge cluster status"
echo "======================="
echo ""

if ! command -v docker >/dev/null 2>&1; then
  echo "docker: NOT FOUND"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon: NOT REACHABLE (run: colima start)"
  exit 1
fi

echo "Docker daemon: OK"
echo ""
printf "%-14s %-12s\n" "CONTAINER" "STATUS"
for name in srs-origin srs-edge-1 srs-edge-2 srs-lb; do
  printf "%-14s %-12s\n" "${name}" "$(docker_container_status "${name}")"
done
echo ""

check_http() {
  local label="$1"
  local url="$2"
  if curl -sf --max-time 5 "${url}" >/dev/null; then
    echo "  OK   ${label}  ${url}"
    return 0
  fi
  echo "  FAIL ${label}  ${url}"
  return 1
}

FAIL=0
check_http "Origin API" "${CLUSTER_ORIGIN_API}/api/v1/streams/" || FAIL=1
check_http "WHEP LB" "${CLUSTER_WHEP_LB}/api/v1/streams/" || FAIL=1
echo ""

if [[ "${FAIL}" -eq 0 ]]; then
  echo "Cluster is healthy — run tests:"
  echo "  export BASE_URL=http://10.255.45.175:8080"
  echo "  ./load-test/srs-suite/run-all-cluster.sh"
  exit 0
fi

echo "Cluster is NOT healthy."
if [[ "$(docker_container_status srs-lb)" == "restarting" ]]; then
  echo ""
  echo "srs-lb logs (last 15 lines):"
  docker logs srs-lb --tail 15 2>&1 || true
fi
echo ""
echo "Fix:"
echo "  ./scripts/stop-srs.sh"
echo "  SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh"
exit 1
