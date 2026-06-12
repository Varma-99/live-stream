#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p load-test/results
TARGET_VUS="${TARGET_VUS:-20}"
DURATION="${DURATION:-120s}"
./load-test/active-stream.sh || true
k6 run \
  --env TARGET_VUS="${TARGET_VUS}" \
  --env BASE_URL="${BASE_URL:-http://127.0.0.1:8080}" \
  --summary-export="load-test/results/hls-k6-$(date +%Y%m%d-%H%M%S).json" \
  load-test/hls-load-test.js
