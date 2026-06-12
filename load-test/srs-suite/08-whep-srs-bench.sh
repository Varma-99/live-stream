#!/usr/bin/env bash
# srs_bench WebRTC play load (optional) + RTMP subscriber fallback.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 08-whep-srs-bench)"
SUBS="${WHEP_SUBSCRIBERS:-100}"
DURATION_SEC="${WHEP_BENCH_SEC:-90}"
require_srs
require_live_stream
require_colima_grpc || true
suite_meta

echo "=== 08 WHEP/WebRTC bench (${SUBS} subs, ${DURATION_SEC}s) ==="
echo "Reports: ${REPORTS_DIR}"

{
  echo "whep_direct=$(whep_url_direct)"
  echo "whep_proxy=$(whep_url_proxy)"
  echo "rtmp_play=$(rtmp_publish_url _high)"
} > "${REPORTS_DIR}/targets.txt"

save_qos_snapshot before-whep-bench
save_srs_streams before-whep-bench

if BENCH="$(sb_rtc_bench_bin)"; then
  RTC_PATH="live/${STREAM_KEY}_high"
  echo "srs_bench WebRTC: webrtc://127.0.0.1/${RTC_PATH} -nn ${SUBS}"
  run_with_timeout "${DURATION_SEC}" "${BENCH}" \
    -sr "webrtc://127.0.0.1/${RTC_PATH}" \
    -nn "${SUBS}" \
    -srs-server=127.0.0.1 \
    2>&1 | tee "${REPORTS_DIR}/srs-bench-webrtc.log" || true
else
  echo "SKIP srs_bench (not built). Install: ./load-test/srs-bench-suite/00-install-srs-bench.sh" \
    | tee "${REPORTS_DIR}/srs-bench-webrtc.log"
fi

if LOAD="$(sb_rtmp_load_bin)"; then
  echo "sb_rtmp_load fallback: ${SUBS} clients"
  run_with_timeout "${DURATION_SEC}" "${LOAD}" -c "${SUBS}" -r "$(rtmp_publish_url _high)" \
    2>&1 | tee "${REPORTS_DIR}/sb-rtmp-load.log" || true
fi

save_qos_snapshot after-whep-bench
save_srs_streams after-whep-bench
save_postmortem after-whep-bench

echo "08 complete."
