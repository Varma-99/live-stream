#!/usr/bin/env bash
# 200-viewer k6 run (requires one LIVE stream). See load-test/README.md
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p load-test/results
echo "Live streams:"
./load-test/active-stream.sh || true
echo ""
k6 run \
  --env TARGET_VUS=200 \
  --summary-export=load-test/results/k6-200-summary.json \
  load-test/k6-viewer-qos.js
