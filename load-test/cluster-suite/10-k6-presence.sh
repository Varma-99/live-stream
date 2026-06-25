#!/usr/bin/env bash
# 10 — Heavy k6 viewer load through HAProxy LB (600 VUs, same class as srs-suite/03).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="10-k6-presence"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

if ! command -v k6 >/dev/null 2>&1; then
  echo "SKIP ${TEST_ID}: k6 not installed (brew install k6)"
  cluster_write_result "SKIP" "k6 not installed"
  exit 0
fi

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

K6_VUS="${K6_PRESENCE_VUS:-600}"
K6_RAMP_UP="${K6_PRESENCE_RAMP_UP_SEC:-180}"
K6_HOLD="${K6_PRESENCE_HOLD_SEC:-180}"
K6_RAMP_DOWN="${K6_PRESENCE_RAMP_DOWN_SEC:-60}"
K6_TOTAL_SEC=$((K6_RAMP_UP + K6_HOLD + K6_RAMP_DOWN))

echo "=== ${TEST_ID} k6 heavy presence via LB ==="
echo "  peak VUs=${K6_VUS}  ramp=${K6_RAMP_UP}s  hold=${K6_HOLD}s  ramp-down=${K6_RAMP_DOWN}s (~${K6_TOTAL_SEC}s)"
echo "  APIs: join, heartbeat, like, qos stats/events, room — through ${BASE_URL}"
echo "  Restart app-1/app-2 after config changes (Redis pool 128, Jetty 256 threads)"

cluster_cleanup_streams
SID="$(cluster_start_stream "${BASE_URL}" "k6-presence")"
echo "k6 stream id=${SID}"
cluster_keepalive_start "${SID}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"
cluster_wait_stream_visible "${SID}" "${BASE_URL}"
export STREAM_ID="${SID}"

k6 run \
  --summary-export="${REPORTS_DIR}/k6-presence-summary.json" \
  --env BASE_URL="${BASE_URL}" \
  --env STREAM_ID="${STREAM_ID}" \
  --env TARGET_VUS="${K6_VUS}" \
  --env RAMP_UP_SEC="${K6_RAMP_UP}" \
  --env HOLD_SEC="${K6_HOLD}" \
  --env RAMP_DOWN_SEC="${K6_RAMP_DOWN}" \
  --env THRESHOLD_JOIN_OK="${THRESHOLD_JOIN_OK:-0.92}" \
  --env THRESHOLD_P95_MS="${THRESHOLD_P95_MS:-5000}" \
  --env THRESHOLD_HTTP_FAIL="${THRESHOLD_HTTP_FAIL:-0.08}" \
  "${ROOT}/load-test/k6-viewer-qos.js" \
  2>&1 | tee "${REPORTS_DIR}/k6-presence.log"

cluster_stop_stream "${SID}"
cluster_pass "k6 heavy presence (${K6_VUS} VUs peak, ${K6_TOTAL_SEC}s scenario)"
