#!/usr/bin/env bash
# Shared helpers for multi-instance app cluster (Phase 7–8).
set -euo pipefail

APP_CLUSTER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export CLUSTER_RUNTIME_DIR="${CLUSTER_RUNTIME_DIR:-/tmp/stream-cluster}"
export CLUSTER_PID_DIR="${CLUSTER_PID_DIR:-${CLUSTER_RUNTIME_DIR}}"
export CLUSTER_LOG_DIR="${CLUSTER_LOG_DIR:-${CLUSTER_RUNTIME_DIR}/logs}"

export DOCKER="${DOCKER:-$(command -v docker 2>/dev/null || echo /opt/homebrew/bin/docker)}"
export COMPOSE="${COMPOSE:-docker-compose}"
export APP_LB_URL="${APP_LB_URL:-http://127.0.0.1:8080}"
export APP1_URL="${APP1_URL:-http://127.0.0.1:8090}"
export APP2_URL="${APP2_URL:-http://127.0.0.1:8092}"
export APP1_ADMIN_URL="${APP1_ADMIN_URL:-http://127.0.0.1:8091}"
export APP2_ADMIN_URL="${APP2_ADMIN_URL:-http://127.0.0.1:8093}"

app_cluster_require_docker() {
  if ! command -v "${DOCKER}" >/dev/null 2>&1; then
    echo "docker not found — install Docker/Colima first" >&2
    exit 1
  fi
  if ! "${DOCKER}" info >/dev/null 2>&1; then
    echo "Docker daemon not reachable — run: colima start" >&2
    exit 1
  fi
}

app_cluster_rcli() {
  "${DOCKER}" exec livestream-redis redis-cli "$@"
}

app_cluster_http_ok() {
  local url="$1"
  curl -sf --max-time 5 "${url}" >/dev/null 2>&1
}

app_cluster_instance_health() {
  local base_url="$1"
  curl -sf --max-time 5 "${base_url}/internal/health" 2>/dev/null \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('instanceId','?'))" 2>/dev/null \
    || echo "FAIL"
}

app_cluster_print_endpoints() {
  echo "  App LB (public):     ${APP_LB_URL}/ui/"
  echo "  App LB health:       ${APP_LB_URL}/internal/health"
  echo "  HAProxy stats:       http://127.0.0.1:8404/stats"
  echo "  App-1 direct:        ${APP1_URL}  (admin ${APP1_ADMIN_URL})"
  echo "  App-2 direct:        ${APP2_URL}  (admin ${APP2_ADMIN_URL})"
  echo "  Gate test:           ./scripts/test-phase8-lb.sh"
}
