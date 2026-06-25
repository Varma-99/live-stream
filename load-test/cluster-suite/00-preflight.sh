#!/usr/bin/env bash
# 00 — Preflight: infra, SRS 2-tier, app cluster, LB.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="00-preflight"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

echo "=== ${TEST_ID} preflight ==="
FAIL=0

check() {
  local label="$1"
  local cmd="$2"
  if eval "${cmd}"; then
    echo "  OK   ${label}"
  else
    echo "  FAIL ${label}" >&2
    FAIL=1
  fi
}

app_cluster_require_docker

check "postgres" "${DOCKER} inspect -f '{{.State.Status}}' livestream-postgres 2>/dev/null | grep -q running"
check "redis" "${DOCKER} inspect -f '{{.State.Status}}' livestream-redis 2>/dev/null | grep -q running"
check "srs-origin" "${DOCKER} inspect -f '{{.State.Status}}' srs-origin 2>/dev/null | grep -q running"
check "srs-playback" "${DOCKER} inspect -f '{{.State.Status}}' srs-playback-1 2>/dev/null | grep -q running"
check "srs-whep-lb" "${DOCKER} inspect -f '{{.State.Status}}' srs-whep-lb 2>/dev/null | grep -q running"
check "app-lb" "${DOCKER} inspect -f '{{.State.Status}}' livestream-app-lb 2>/dev/null | grep -q running"
check "app-1" "app_cluster_http_ok ${APP1_URL}/internal/health"
check "app-2" "app_cluster_http_ok ${APP2_URL}/internal/health"
check "LB" "app_cluster_http_ok ${BASE_URL}/internal/health"
check "origin-api" "curl -sf --max-time 5 ${CLUSTER_ORIGIN_API}/api/v1/streams/ >/dev/null"
check "whep-lb" "curl -sf --max-time 5 ${CLUSTER_WHEP_LB}/api/v1/streams/ >/dev/null"

python3 - <<PY > "${REPORTS_DIR}/suite-meta.json"
import json, os
print(json.dumps({
  "test_id": "${TEST_ID}",
  "base_url": os.environ.get("BASE_URL"),
  "app1": os.environ.get("APP1_URL"),
  "app2": os.environ.get("APP2_URL"),
}, indent=2))
PY

[[ "${FAIL}" -eq 0 ]] || cluster_fail "preflight checks failed"
cluster_pass "all cluster components healthy"
