#!/usr/bin/env bash
# Stop cluster started by scripts/start-cluster.sh (reverse order).
#
# Usage:
#   ./scripts/stop-cluster.sh
#   ./scripts/stop-cluster.sh --keep-infra    # leave PostgreSQL + Redis running
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/cluster-lib.sh
source "${ROOT}/scripts/lib/cluster-lib.sh"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

KEEP_INFRA=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-infra) KEEP_INFRA=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--keep-infra]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

cd "${ROOT}"

echo "Stopping live-stream cluster…"

echo "  app LB…"
cluster_compose -f docker-compose.app-lb.yml down --remove-orphans 2>/dev/null || true
rm -f "${CLUSTER_PID_DIR}/app-lb.pid"

echo "  app-2…"
cluster_stop_pid "app-2"

echo "  app-1…"
cluster_stop_pid "app-1"

echo "  SRS…"
"${ROOT}/scripts/stop-srs.sh" 2>/dev/null || true

if [[ "${KEEP_INFRA}" -eq 1 ]]; then
  echo "  infra (kept — postgres + redis still running)"
else
  echo "  infra…"
  cluster_compose -f docker-compose.dev-infra.yml down --remove-orphans 2>/dev/null || true
fi

echo "Cluster stopped."
