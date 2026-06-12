#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 03-k6-viewers-600)"
require_live_stream
suite_meta

VUS="${K6_VIEWERS_MAX_VUS:-600}"
SUMMARY="${REPORTS_DIR}/k6-viewers-600-summary.json"

echo "=== 03 k6 viewer presence (${VUS} VUs) ==="
echo "Reports: ${REPORTS_DIR}"
echo "Ensure database.maxSize: 128 in config-dev.yml"

save_qos_snapshot before-k6-600

k6 run \
  --env BASE_URL="${BASE_URL}" \
  --env STREAM_ID="${STREAM_ID}" \
  --env TARGET_VUS="${VUS}" \
  --env RAMP_UP_SEC=120 \
  --env HOLD_SEC=180 \
  --env RAMP_DOWN_SEC=60 \
  --summary-export="${SUMMARY}" \
  "${ROOT}/load-test/k6-viewer-qos.js" \
  2>&1 | tee "${REPORTS_DIR}/k6-viewers-600.log"

save_qos_snapshot after-k6-600
save_postmortem after-k6-600

echo "03 complete."
