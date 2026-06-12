#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 02-k6-viewers-200)"
require_live_stream
suite_meta

VUS="${K6_VIEWERS_VUS:-200}"
SUMMARY="${REPORTS_DIR}/k6-viewers-summary.json"

echo "=== 02 k6 viewer presence (${VUS} VUs) ==="
echo "Reports: ${REPORTS_DIR}"

save_qos_snapshot before-k6
save_srs_streams before-k6

k6 run \
  --env BASE_URL="${BASE_URL}" \
  --env STREAM_ID="${STREAM_ID}" \
  --env TARGET_VUS="${VUS}" \
  --summary-export="${SUMMARY}" \
  "${ROOT}/load-test/k6-viewer-qos.js" \
  2>&1 | tee "${REPORTS_DIR}/k6-viewers.log"

save_qos_snapshot after-k6
save_postmortem after-k6
save_srs_streams after-k6

echo "02 complete. Summary → ${SUMMARY}"
