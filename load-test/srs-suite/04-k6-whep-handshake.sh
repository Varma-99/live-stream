#!/usr/bin/env bash
# k6 WHEP HTTP signaling load via app proxy → SRS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 04-k6-whep-handshake)"
require_srs
require_live_stream
suite_meta

VUS="${K6_WHEP_VUS:-50}"
SUMMARY="${REPORTS_DIR}/k6-whep-summary.json"

echo "=== 04 k6 WHEP handshake (${VUS} VUs) ==="
echo "Target: $(whep_url_proxy)"
echo "Reports: ${REPORTS_DIR}"

save_srs_streams before-whep-k6

k6 run \
  --env BASE_URL="${BASE_URL}" \
  --env STREAM_KEY="${STREAM_KEY}" \
  --env TARGET_VUS="${VUS}" \
  --summary-export="${SUMMARY}" \
  "${ROOT}/load-test/k6-whep-handshake.js" \
  2>&1 | tee "${REPORTS_DIR}/k6-whep.log"

save_qos_snapshot after-whep-k6
save_srs_streams after-whep-k6

echo "04 complete."
