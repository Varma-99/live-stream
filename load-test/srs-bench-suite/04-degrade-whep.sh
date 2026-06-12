#!/usr/bin/env bash
# Test 4: FFmpeg degrade/restore while a small subscriber load runs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"

REPORTS_DIR="$(report_dir 04-degrade)"
SUBS="${DEGRADE_SUBS:-10}"
LOAD_SEC="${DEGRADE_LOAD_SEC:-120}"
require_live_stream

echo "=== Test 4: Degrade/restore + ${SUBS} subs ==="
echo "Reports: ${REPORTS_DIR}"

RTMP_PLAY="$(rtmp_publish_url _high)"
LOAD_PID=""

cleanup() {
  if [[ -n "${LOAD_PID}" ]]; then
    kill "${LOAD_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if LOAD="$(sb_rtmp_load_bin)"; then
  echo "Background RTMP subscribers: ${SUBS} → ${RTMP_PLAY}"
  "${LOAD}" -c "${SUBS}" -r "${RTMP_PLAY}" \
    > "${REPORTS_DIR}/sb-rtmp-load.log" 2>&1 &
  LOAD_PID=$!
elif BENCH="$(sb_rtc_bench_bin)"; then
  echo "Background srs_bench: ${SUBS} players"
  "${BENCH}" -sr "webrtc://127.0.0.1/live/${STREAM_KEY}_high" -nn "${SUBS}" -srs-server=127.0.0.1 \
    > "${REPORTS_DIR}/srs-bench.log" 2>&1 &
  LOAD_PID=$!
else
  echo "WARN: no srs-bench binary; degrade test runs without subscriber load" >&2
fi

sleep 5
save_qos_snapshot before-degrade

echo "POST degrade…"
curl -sf -X POST "${BASE_URL}/streams/${STREAM_ID}/degrade" \
  | tee "${REPORTS_DIR}/degrade-response.json"
echo ""

echo "Waiting 30s (FFmpeg restart + paths settle)…"
sleep 30
save_qos_snapshot after-degrade

echo "POST restore-quality…"
curl -sf -X POST "${BASE_URL}/streams/${STREAM_ID}/restore-quality" \
  | tee "${REPORTS_DIR}/restore-response.json"
echo ""

echo "Waiting 30s after restore…"
sleep 30
save_qos_snapshot after-restore
save_postmortem after-degrade-test

echo "Test 4 complete. Compare war room + ${REPORTS_DIR}"

# keep load running until end of script budget
remaining=$((LOAD_SEC - 70))
if [[ "${remaining}" -gt 0 && -n "${LOAD_PID}" ]]; then
  sleep "${remaining}"
fi
