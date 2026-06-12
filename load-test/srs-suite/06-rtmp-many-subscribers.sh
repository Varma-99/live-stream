#!/usr/bin/env bash
# Many RTMP read clients on app's _high path (delivery load without WebRTC UDP).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 06-rtmp-many-subscribers)"
SUBS="${RTMP_SUBSCRIBER_COUNT:-100}"
DURATION_SEC="${RTMP_SUBSCRIBE_SEC:-90}"
require_srs
require_live_stream
suite_meta

LOAD_BIN="$(sb_rtmp_load_bin)" || {
  echo "ERROR: sb_rtmp_load missing" >&2
  exit 1
}

RTMP_PLAY="$(rtmp_publish_url _high)"
echo "=== 06 ${SUBS} RTMP subscribers (${DURATION_SEC}s) ==="
echo "Play URL: ${RTMP_PLAY}"
echo "Reports: ${REPORTS_DIR}"

save_qos_snapshot before-subs
save_srs_streams before-subs

run_with_timeout "${DURATION_SEC}" "${LOAD_BIN}" -c "${SUBS}" -r "${RTMP_PLAY}" \
  2>&1 | tee "${REPORTS_DIR}/sb-rtmp-load.log" || true

save_qos_snapshot after-subs
save_srs_streams after-subs
save_postmortem after-subs

echo "06 complete."
