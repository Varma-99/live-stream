#!/usr/bin/env bash
# 02 — Cross-instance broadcaster heartbeat (Phase 6).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="02-cross-instance-broadcaster"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

echo "=== ${TEST_ID} cross-instance broadcaster ==="
cluster_cleanup_streams
SID="$(cluster_start_stream "${APP1_URL}" "xbroadcaster")"
echo "started on app-1 stream id=${SID}"
cluster_keepalive_start "${SID}" "${APP2_URL}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"

OWNER="$(cluster_redis GET "stream:owner:${SID}")"
[[ "${OWNER}" == "app-1" ]] || cluster_fail "owner expected app-1 got ${OWNER}"

for _ in 1 2 3; do
  cluster_curl_json POST "${APP2_URL}/streams/${SID}/broadcaster-heartbeat" >/dev/null
  sleep 1
done
TTL="$(cluster_redis TTL "broadcaster:heartbeat:${SID}")"
echo "heartbeat TTL after app-2 touches=${TTL}"
[[ "${TTL}" -gt 0 ]] || cluster_fail "heartbeat not refreshed via app-2"

cluster_stop_stream "${SID}" "${APP1_URL}"
cluster_pass "heartbeat via app-2 keeps stream alive on app-1"
