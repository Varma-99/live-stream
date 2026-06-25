#!/usr/bin/env bash
# Health check for 2-instance app cluster + HAProxy (Phase 8).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/app-cluster.sh
source "${ROOT}/scripts/lib/app-cluster.sh"

cd "${ROOT}"
FAIL=0

check_line() {
  local label="$1"
  local result="$2"
  if [[ "${result}" == "FAIL" || "${result}" == "missing" ]]; then
    printf "  FAIL %-22s %s\n" "${label}" "${result}"
    FAIL=1
  else
    printf "  OK   %-22s %s\n" "${label}" "${result}"
  fi
}

echo "App cluster status"
echo "=================="
echo ""

if command -v "${DOCKER}" >/dev/null 2>&1 && "${DOCKER}" info >/dev/null 2>&1; then
  check_line "postgres" "$("${DOCKER}" inspect -f '{{.State.Status}}' livestream-postgres 2>/dev/null || echo missing)"
  check_line "redis" "$("${DOCKER}" inspect -f '{{.State.Status}}' livestream-redis 2>/dev/null || echo missing)"
  check_line "app-lb" "$("${DOCKER}" inspect -f '{{.State.Status}}' livestream-app-lb 2>/dev/null || echo missing)"
else
  echo "  Docker: not available"
  FAIL=1
fi

echo ""
check_line "app-1" "$(app_cluster_instance_health "${APP1_URL}")"
check_line "app-2" "$(app_cluster_instance_health "${APP2_URL}")"
check_line "LB → instance" "$(app_cluster_instance_health "${APP_LB_URL}")"

if app_cluster_http_ok "http://127.0.0.1:8404/stats"; then
  check_line "haproxy stats" "http://127.0.0.1:8404/stats"
else
  check_line "haproxy stats" "FAIL"
fi

echo ""
if [[ "${FAIL}" -eq 0 ]]; then
  echo "Cluster is healthy."
  app_cluster_print_endpoints
  exit 0
fi

echo "Cluster is NOT healthy."
echo ""
echo "Typical fix:"
echo "  docker-compose -f docker-compose.dev-infra.yml up -d"
echo "  ./mvnw -Dexec.args=\"server config/config-dev-pg-app1.yml\" compile exec:java   # terminal 1"
echo "  ./mvnw -Dexec.args=\"server config/config-dev-pg-app2.yml\" compile exec:java   # terminal 2"
echo "  ./scripts/start-app-lb.sh"
exit 1
