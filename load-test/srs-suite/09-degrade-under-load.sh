#!/usr/bin/env bash
# FFmpeg degrade/restore while RTMP subscribers hold delivery load.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 09-degrade-under-load)"
SUBS="${DEGRADE_SUBS:-15}"
LOAD_SEC="${DEGRADE_LOAD_SEC:-150}"
require_srs
require_live_stream
suite_meta

LOAD_PID=""
cleanup() { [[ -n "${LOAD_PID}" ]] && kill "${LOAD_PID}" 2>/dev/null || true; }
trap cleanup EXIT

echo "=== 09 degrade under load (${SUBS} subs) ==="
echo "Reports: ${REPORTS_DIR}"

if LOAD="$(sb_rtmp_load_bin)"; then
  "${LOAD}" -c "${SUBS}" -r "$(rtmp_publish_url _high)" \
    > "${REPORTS_DIR}/sb-rtmp-load.log" 2>&1 &
  LOAD_PID=$!
fi

sleep 5
save_qos_snapshot before-degrade
save_srs_streams before-degrade

curl -sf -X POST "${BASE_URL}/streams/${STREAM_ID}/degrade" \
  | tee "${REPORTS_DIR}/degrade-response.json"
echo ""
sleep 35
save_qos_snapshot after-degrade
save_srs_streams after-degrade

curl -sf -X POST "${BASE_URL}/streams/${STREAM_ID}/restore-quality" \
  | tee "${REPORTS_DIR}/restore-response.json"
echo ""
sleep 35
save_qos_snapshot after-restore
save_srs_streams after-restore
save_postmortem after-degrade

remaining=$((LOAD_SEC - 80))
[[ "${remaining}" -gt 0 && -n "${LOAD_PID}" ]] && sleep "${remaining}"

echo "09 complete."
