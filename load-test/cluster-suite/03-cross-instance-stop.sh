#!/usr/bin/env bash
# 03 — Cross-instance stop (Phase 7): start app-1, stop app-2 (+ reverse).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="03-cross-instance-stop"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

echo "=== ${TEST_ID} cross-instance stop ==="
cluster_cleanup_streams

# Case A: start app-1, stop app-2
SID1="$(cluster_start_stream "${APP1_URL}" "xstop-a")"
cluster_keepalive_start "${SID1}" "${APP1_URL}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"
sleep 1
ST="$(cluster_curl_json POST "${APP2_URL}/streams/${SID1}/stop" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"
[[ "${ST}" == "ENDED" ]] || cluster_fail "case A stop failed: ${ST}"
echo "case A PASS (start app-1, stop app-2)"

# Case B: start app-2, stop app-1
SID2="$(cluster_start_stream "${APP2_URL}" "xstop-b")"
cluster_keepalive_start "${SID2}" "${APP2_URL}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"
sleep 1
ST2="$(cluster_curl_json POST "${APP1_URL}/streams/${SID2}/stop" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"
[[ "${ST2}" == "ENDED" ]] || cluster_fail "case B stop failed: ${ST2}"
echo "case B PASS (start app-2, stop app-1)"

cluster_pass "bidirectional cross-instance stop"
