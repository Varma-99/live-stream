#!/usr/bin/env bash
# Start app-tier HAProxy load balancer on :8080 → app-1 :8090, app-2 :8092.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/cluster-lib.sh
source "${ROOT}/scripts/lib/cluster-lib.sh"

cd "${ROOT}"
app_cluster_require_docker

if ! app_cluster_http_ok "${APP1_URL}/internal/health"; then
  echo "WARN: app-1 not reachable at ${APP1_URL}" >&2
  echo "      Start: ./scripts/start-cluster.sh  (or app-1 manually)" >&2
fi
if ! app_cluster_http_ok "${APP2_URL}/internal/health"; then
  echo "WARN: app-2 not reachable at ${APP2_URL}" >&2
  echo "      Start: ./scripts/start-cluster.sh  (or app-2 manually)" >&2
fi

if "${DOCKER}" ps --filter name=livestream-app-lb --filter status=running -q 2>/dev/null | grep -q .; then
  echo "App LB is already running."
  app_cluster_print_endpoints
  exit 0
fi

if app_cluster_http_ok "${APP_LB_URL}/internal/health"; then
  echo "App LB already reachable at ${APP_LB_URL} (port 8080 in use)."
  app_cluster_print_endpoints
  exit 0
fi

if cluster_port_in_use 8080; then
  echo "Port 8080 is in use but the app LB is not healthy." >&2
  lsof -nP -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true
  echo "" >&2
  echo "Common fix (stale Colima port forward after container stop):" >&2
  echo "  ./scripts/stop-cluster.sh && colima stop && colima start --port-forwarder=grpc" >&2
  echo "  ./scripts/start-cluster.sh --skip-infra --skip-srs" >&2
  echo "" >&2
  echo "If a single-app dev server is on :8080, stop it first." >&2
  exit 1
fi

echo "Starting app load balancer (HAProxy :8080)…"
cluster_compose -f docker-compose.app-lb.yml up -d

wait_for_health "app-lb" "${APP_LB_URL}/internal/health" 15 || {
  echo "FAIL: LB not responding on ${APP_LB_URL}" >&2
  "${DOCKER}" logs livestream-app-lb --tail 20 2>&1 || true
  exit 1
}

echo "App LB is up."
app_cluster_print_endpoints
