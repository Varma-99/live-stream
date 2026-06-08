#!/usr/bin/env bash
# 600-viewer k6 run (requires one LIVE stream). See load-test/README.md
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p load-test/results
echo "Live streams:"
./load-test/active-stream.sh || true
echo ""
k6 run \
  --env TARGET_VUS=600 \
  --env RAMP_UP_SEC=120 \
  --env HOLD_SEC=180 \
  --env RAMP_DOWN_SEC=60 \
  --summary-export=load-test/results/k6-600-summary.json \
  load-test/k6-viewer-qos.js
