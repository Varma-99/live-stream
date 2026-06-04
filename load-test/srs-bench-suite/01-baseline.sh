#!/usr/bin/env bash
# Test 1: FFmpeg baseline — save QoS + postmortem while app is publishing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=../lib/common.sh
source "${ROOT}/load-test/lib/common.sh"

REPORTS_DIR="$(report_dir 01-baseline)"
require_live_stream

echo "=== Test 1: FFmpeg baseline ==="
echo "Open broadcast war room and confirm ingest/encode graphs look healthy."
echo "Reports dir: ${REPORTS_DIR}"

save_qos_snapshot baseline
save_postmortem baseline

cat > "${REPORTS_DIR}/README.txt" <<EOF
Test 1 — FFmpeg baseline
Stream id: ${STREAM_ID}
Stream key: ${STREAM_KEY}

Manual: screenshot broadcast war room (QoS panel).
Files: qos-baseline.json, postmortem-baseline.json
EOF

echo "Test 1 complete."
