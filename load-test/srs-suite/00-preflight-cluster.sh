#!/usr/bin/env bash
# Cluster preflight — origin :1995, WHEP LB :1985, edges, app, optional k6.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"
source "${ROOT}/load-test/lib/srs-cluster.sh"

apply_cluster_env
REPORTS_DIR="$(report_dir_cluster 00-preflight)"
suite_meta_cluster

echo "=== SRS cluster preflight ==="
echo "Origin API: ${CLUSTER_ORIGIN_API}"
echo "WHEP LB:    ${CLUSTER_WHEP_LB}"
echo "Reports:    ${REPORTS_DIR}"

FAIL=0
check() {
  local name="$1"
  shift
  if "$@"; then
    echo "  OK   ${name}"
  else
    echo "  FAIL ${name}"
    FAIL=1
  fi
}

check "origin API :1995" curl -sf --max-time 5 "${CLUSTER_ORIGIN_API}/api/v1/streams/" >/dev/null
check "WHEP LB :1985" curl -sf --max-time 5 "${CLUSTER_WHEP_LB}/api/v1/streams/" >/dev/null
check "cluster containers" require_cluster_stack
check "App :8080" require_app
check "k6 installed" command -v k6 >/dev/null 2>&1
check "min WHEP SDP fixture" test -f "${CLUSTER_MIN_SDP}"

require_colima_grpc || true

ensure_live_stream_or_dummy
save_qos_snapshot preflight
  save_srs_streams preflight
  save_edge_streams "${CLUSTER_EDGE1_NAME}" preflight
  save_edge_streams "${CLUSTER_EDGE2_NAME}" preflight
  save_origin_pull_stats preflight

{
  echo "cluster_origin_api=${CLUSTER_ORIGIN_API}"
  echo "cluster_whep_lb=${CLUSTER_WHEP_LB}"
  echo "colima_portForwarder=$(awk '/^portForwarder:/ {print $2}' "${HOME}/.colima/default/colima.yaml" 2>/dev/null || echo unknown)"
  echo "lan_ip=$(ipconfig getifaddr en0 2>/dev/null || echo unknown)"
  curl -sf "${BASE_URL}/config/public" 2>/dev/null || echo "config_public=unavailable"
} > "${REPORTS_DIR}/environment.txt"

if [[ "${FAIL}" -ne 0 ]]; then
  echo ""
  echo "Cluster preflight FAILED."
  exit 1
fi

echo ""
echo "Cluster preflight passed."
