#!/usr/bin/env bash
# 04 — Viewer presence via LB; Redis ZCOUNT vs API.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="04-viewer-presence"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

echo "=== ${TEST_ID} viewer presence ==="
cluster_cleanup_streams
SID="$(cluster_start_stream "${BASE_URL}" "presence-test")"
cluster_keepalive_start "${SID}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"

JOIN1="$(cluster_curl_json POST "${BASE_URL}/streams/${SID}/join" | python3 -c "import sys,json; print(json.load(sys.stdin)['presenceId'])")"
JOIN2="$(cluster_curl_json POST "${APP2_URL}/streams/${SID}/join" | python3 -c "import sys,json; print(json.load(sys.stdin)['presenceId'])")"
echo "joined presence ${JOIN1:0:8}... and ${JOIN2:0:8}..."

cluster_curl_json POST "${BASE_URL}/streams/${SID}/heartbeat" \
  "{\"presenceId\":\"${JOIN1}\"}" >/dev/null
cluster_curl_json POST "${APP2_URL}/streams/${SID}/heartbeat" \
  "{\"presenceId\":\"${JOIN2}\"}" >/dev/null

ZCOUNT="$(cluster_redis ZCOUNT "room:${SID}:viewers" -inf +inf)"
ROOM="$(cluster_curl_json GET "${BASE_URL}/streams/${SID}/room" | python3 -c "import sys,json; print(json.load(sys.stdin)['viewers'])")"
echo "redis zcount=${ZCOUNT} api viewers=${ROOM}"

if [[ "${ZCOUNT}" -lt 2 || "${ROOM}" -lt 2 ]]; then
  cluster_fail "expected at least 2 viewers (zcount=${ZCOUNT} api=${ROOM})"
fi

cluster_curl_json POST "${BASE_URL}/streams/${SID}/leave" \
  "{\"presenceId\":\"${JOIN1}\"}" >/dev/null
cluster_curl_json POST "${APP2_URL}/streams/${SID}/leave" \
  "{\"presenceId\":\"${JOIN2}\"}" >/dev/null

cluster_stop_stream "${SID}"
cluster_pass "join/heartbeat/leave across instances"
