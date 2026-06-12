#!/usr/bin/env bash
# Capture baseline QoS + SRS API while app FFmpeg publishes to SRS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 01-baseline-qos)"
require_srs
require_live_stream
suite_meta

echo "=== 01 baseline QoS (stream ${STREAM_ID}) ==="
echo "Reports: ${REPORTS_DIR}"

for i in 1 2 3; do
  save_qos_snapshot "sample-${i}"
  save_srs_streams "sample-${i}"
  sleep 5
done
save_postmortem baseline
save_ops_qos

cat > "${REPORTS_DIR}/README.txt" <<EOF
01 — Baseline QoS (SRS)
Stream: id=${STREAM_ID} key=${STREAM_KEY}
RTMP: $(rtmp_publish_url _high)
WHEP proxy: $(whep_url_proxy)
WHEP direct: $(whep_url_direct)
FLV: $(srs_flv_url)
EOF

echo "01 complete."
