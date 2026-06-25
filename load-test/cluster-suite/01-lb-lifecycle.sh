#!/usr/bin/env bash
# 01 — Stream lifecycle via LB: start, pause, resume, stop.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="01-lb-lifecycle"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

echo "=== ${TEST_ID} LB lifecycle ==="
cluster_cleanup_streams
SID="$(cluster_start_stream "${BASE_URL}" "lifecycle-test")"
echo "stream id=${SID}"

cluster_curl_json POST "${BASE_URL}/streams/${SID}/pause" >/dev/null
PS="$(cluster_redis GET "stream:status:${SID}")"
echo "after pause redis status=${PS}"
[[ "${PS}" == "PAUSED" ]] || cluster_fail "expected PAUSED got ${PS}"

cluster_curl_json POST "${BASE_URL}/streams/${SID}/resume" >/dev/null
RS="$(cluster_redis GET "stream:status:${SID}")"
echo "after resume redis status=${RS}"
[[ "${RS}" == "LIVE" ]] || cluster_fail "expected LIVE got ${RS}"

cluster_stop_stream "${SID}" "${BASE_URL}"
ES="$(cluster_redis GET "stream:status:${SID}")"
echo "after stop redis status=${ES}"
[[ "${ES}" == "ENDED" ]] || cluster_fail "expected ENDED got ${ES}"

cluster_pass "pause/resume/stop via LB synced with Redis"
