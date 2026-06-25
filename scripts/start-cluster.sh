#!/usr/bin/env bash
# Start full dev cluster: Colima → PG/Redis → SRS 2-tier → app-1/2 (JAR) → HAProxy LB.
#
# Usage:
#   ./scripts/start-cluster.sh
#   ./scripts/start-cluster.sh --skip-build
#   ./scripts/start-cluster.sh --skip-infra --skip-srs   # restart apps + LB only
#
# Stop: ./scripts/stop-cluster.sh  (or Ctrl+C)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/cluster-lib.sh
source "${ROOT}/scripts/lib/cluster-lib.sh"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

SKIP_INFRA=0
SKIP_SRS=0
SKIP_BUILD=0
KEEP_INFRA_ON_STOP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-infra) SKIP_INFRA=1; shift ;;
    --skip-srs) SKIP_SRS=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --keep-infra) KEEP_INFRA_ON_STOP=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--skip-infra] [--skip-srs] [--skip-build] [--keep-infra]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

export SRS_STACK=2tier
CLUSTER_CLEANUP_DONE=0

cluster_cleanup() {
  local code="${1:-0}"
  if [[ "${CLUSTER_CLEANUP_DONE}" -eq 1 ]]; then
    exit "${code}"
  fi
  CLUSTER_CLEANUP_DONE=1
  trap - EXIT INT TERM
  echo ""
  echo "Interrupted — stopping cluster…"
  if [[ "${KEEP_INFRA_ON_STOP}" -eq 1 ]]; then
    "${ROOT}/scripts/stop-cluster.sh" --keep-infra || true
  else
    "${ROOT}/scripts/stop-cluster.sh" || true
  fi
  exit "${code}"
}

trap 'cluster_cleanup 130' INT TERM

cd "${ROOT}"
cluster_ensure_dirs

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Starting live-stream cluster                                 ║"
echo "╚══════════════════════════════════════════════════════════════╝"

cluster_step "1. Docker / Colima"
cluster_require_colima

if [[ "${SKIP_INFRA}" -eq 0 ]]; then
  cluster_step "2. PostgreSQL + Redis"
  cluster_compose -f docker-compose.dev-infra.yml up -d
  wait_for_cmd "postgres" \
    "${DOCKER} exec livestream-postgres pg_isready -U livestream -d livestream" 15
  wait_for_cmd "redis" \
    "${DOCKER} exec livestream-redis redis-cli PING | grep -q PONG" 5
else
  cluster_step "2. PostgreSQL + Redis (skipped)"
  wait_for_cmd "postgres" \
    "${DOCKER} exec livestream-postgres pg_isready -U livestream -d livestream" 5
  wait_for_cmd "redis" \
    "${DOCKER} exec livestream-redis redis-cli PING | grep -q PONG" 5
fi

if [[ "${SKIP_SRS}" -eq 0 ]]; then
  cluster_step "3. SRS 2-tier"
  if srs_2tier_running; then
    cluster_info "SRS 2-tier already running"
  else
    "${ROOT}/scripts/start-srs.sh"
  fi
  wait_for_health "srs-origin" "http://127.0.0.1:1995/api/v1/streams/" 15
  wait_for_health "srs-whep-lb" "http://127.0.0.1:1985/api/v1/streams/" 15
  wait_for_cmd "srs-playback" \
    "${DOCKER} inspect -f '{{.State.Status}}' srs-playback-1 2>/dev/null | grep -q running" 15
else
  cluster_step "3. SRS 2-tier (skipped)"
  wait_for_health "srs-origin" "http://127.0.0.1:1995/api/v1/streams/" 5 || true
fi

cluster_step "4. Build application JAR"
JAR=""
if [[ "${SKIP_BUILD}" -eq 1 ]]; then
  JAR="$(cluster_find_jar)" || cluster_fail "no JAR in target/ — run without --skip-build"
  cluster_info "using existing ${JAR}"
else
  cluster_info "mvn package -DskipTests (one build for both instances)…"
  ./mvnw -q package -DskipTests
  JAR="$(cluster_find_jar)" || cluster_fail "mvn package did not produce target/live-stream-*.jar"
  cluster_info "built ${JAR}"
fi

cluster_step "5. App instance 1 (:8090)"
cluster_start_java_app "app-1" "config-dev-pg-app1.yml" 8090 "${JAR}" 60

cluster_step "6. App instance 2 (:8092)"
cluster_start_java_app "app-2" "config-dev-pg-app2.yml" 8092 "${JAR}" 60

cluster_step "7. App load balancer (:8080)"
if "${DOCKER}" ps --filter name=livestream-app-lb --filter status=running -q 2>/dev/null | grep -q .; then
  cluster_info "app LB container already running"
elif app_cluster_http_ok "${APP_LB_URL}/internal/health"; then
  cluster_info "app LB already healthy at ${APP_LB_URL}"
else
  if cluster_port_in_use 8080 && ! app_cluster_http_ok "${APP_LB_URL}/internal/health"; then
    echo "Port 8080 in use but LB not healthy (stale Colima forward?)." >&2
    lsof -nP -iTCP:8080 -sTCP:LISTEN 2>/dev/null || true
    cluster_fail "free port 8080: colima stop && colima start --port-forwarder=grpc, then retry"
  fi
  cluster_info "starting HAProxy via docker-compose.app-lb.yml"
  cluster_compose -f docker-compose.app-lb.yml up -d
fi
wait_for_health "app-lb" "${APP_LB_URL}/internal/health" 15
echo "livestream-app-lb" >"${CLUSTER_PID_DIR}/app-lb.pid"

cluster_step "8. Smoke test"
if ! cluster_smoke_stream; then
  echo "" >&2
  echo "Smoke test failed. Cluster left running for debugging." >&2
  echo "  logs: ${CLUSTER_LOG_DIR}/" >&2
  echo "  stop: ./scripts/stop-cluster.sh" >&2
  exit 1
fi

CLUSTER_CLEANUP_DONE=1

cluster_print_summary
