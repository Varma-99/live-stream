#!/usr/bin/env bash
# 07 — Redis restart mid-stream: grace period prevents false zombie stop.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="07-redis-restart-grace"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

echo "=== ${TEST_ID} Redis restart grace ==="
cluster_cleanup_streams
SID="$(cluster_start_stream "${APP1_URL}" "redis-grace")"
cluster_keepalive_start "${SID}" "${BASE_URL}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"
sleep 2

echo "restarting Redis..."
"${DOCKER}" restart livestream-redis >/dev/null
sleep 3

# Wait for Redis healthy
for _ in $(seq 1 20); do
  if cluster_redis PING 2>/dev/null | grep -q PONG; then
    break
  fi
  sleep 1
done

cluster_redis PING | grep -q PONG || cluster_fail "redis did not recover"

echo "waiting 25s (within 30s grace) — stream should stay LIVE..."
sleep 25

STATUS="$(cluster_curl_json GET "${BASE_URL}/streams" \
  | python3 -c "import sys,json; d=[x for x in json.load(sys.stdin) if x['id']==${SID}]; print(d[0]['status'] if d else 'GONE')")"
echo "stream status after redis restart + grace=${STATUS}"

if [[ "${STATUS}" != "LIVE" ]]; then
  cluster_fail "stream ended during redis grace (false zombie?)"
fi

# Owner should repopulate on refresh cycle
OWNER="$(cluster_redis GET "stream:owner:${SID}")"
echo "stream:owner=${OWNER}"

cluster_stop_stream "${SID}" "${BASE_URL}"
cluster_pass "no false zombie stop during Redis restart grace"
