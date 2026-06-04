#!/usr/bin/env bash
# Test 2: ~100 delivery clients — WHEP/WebRTC (srs_bench) + RTMP read fallback.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"

REPORTS_DIR="$(report_dir 02-whep-100)"
SUBS="${WHEP_SUBSCRIBERS:-100}"
DURATION_SEC="${WHEP_DURATION_SEC:-90}"
require_live_stream

echo "=== Test 2: ${SUBS} subscribers (stream ${STREAM_ID} / ${STREAM_KEY}) ==="
echo "Reports: ${REPORTS_DIR}"
echo "Keep app FFmpeg publishing to $(rtmp_publish_url _high) during this test."

WHEP_DIRECT="$(whep_url_direct)"
WHEP_PROXY="$(whep_url_proxy)"
RTMP_PLAY="$(rtmp_publish_url _high)"

{
  echo "stream_id=${STREAM_ID}"
  echo "stream_key=${STREAM_KEY}"
  echo "whep_direct=${WHEP_DIRECT}"
  echo "whep_proxy=${WHEP_PROXY}"
  echo "rtmp_play=${RTMP_PLAY}"
} > "${REPORTS_DIR}/targets.txt"

save_qos_snapshot before-load

# --- A: srs_bench WebRTC play (feature/rtc) — optional; often skipped on Mac ---
if BENCH="$(sb_rtc_bench_bin)"; then
  RTC_PATH="live/${STREAM_KEY}_high"
  echo "Running srs_bench WebRTC play (-nn ${SUBS}) for ${DURATION_SEC}s…"
  echo "URL: webrtc://127.0.0.1/${RTC_PATH}"
  run_with_timeout "${DURATION_SEC}" "${BENCH}" \
    -sr "webrtc://127.0.0.1/${RTC_PATH}" \
    -nn "${SUBS}" \
    -srs-server=127.0.0.1 \
    2>&1 | tee "${REPORTS_DIR}/srs-bench-webrtc.log" || true
else
  echo "SKIP: srs_bench not built (optional). Install: brew install go srt && cd ~/srs-bench-rtc && make" \
    | tee "${REPORTS_DIR}/srs-bench-webrtc.log"
fi

# --- B: RTMP subscribers (master) — primary delivery load when WebRTC bench missing ---
if LOAD="$(sb_rtmp_load_bin)"; then
  echo "Running sb_rtmp_load -c ${SUBS} on RTMP play for ${DURATION_SEC}s…"
  run_with_timeout "${DURATION_SEC}" "${LOAD}" -c "${SUBS}" -r "${RTMP_PLAY}" \
    2>&1 | tee "${REPORTS_DIR}/sb-rtmp-load.log" || true
else
  echo "ERROR: sb_rtmp_load not found. Run 00-install-srs-bench.sh (master branch)." | tee "${REPORTS_DIR}/sb-rtmp-load.log"
  exit 1
fi

# --- C: Document WHEP HTTP endpoints for manual / other tools ---
cat > "${REPORTS_DIR}/whep-http-endpoints.txt" <<EOF
MediaMTX WHEP (direct):  POST ${WHEP_DIRECT}
App proxy WHEP:          POST ${WHEP_PROXY}

If srs_bench WebRTC fails against MediaMTX, use a browser smoke test on:
  ${BASE_URL}/ui/viewer.html?stream=${STREAM_ID}

Optional API load (not real WebRTC): k6 run --env TARGET_VUS=${SUBS} load-test/k6-viewer-qos.js
EOF

save_qos_snapshot after-load
save_postmortem after-load

echo "Test 2 complete. Review logs in ${REPORTS_DIR}"
