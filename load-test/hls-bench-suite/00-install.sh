#!/usr/bin/env bash
# HLS load test — requires k6 only.
set -euo pipefail
if ! command -v k6 >/dev/null 2>&1; then
  echo "Install k6: brew install k6"
  exit 1
fi
k6 version
echo "Ready. Run: ./load-test/hls-bench-suite/01-run.sh"
