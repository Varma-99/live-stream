#!/usr/bin/env bash
# Run all 4 srs-bench suite tests in order.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

run_step() {
  local script="$1"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Running ${script}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  "${DIR}/${script}"
}

run_step 01-baseline.sh
read -r -p "Test 1 done. Press Enter for Test 2 (100 subs)…" _

run_step 02-whep-100.sh
read -r -p "Test 2 done. Press Enter for Test 3 (many RTMP)…" _

run_step 03-rtmp-many.sh
read -r -p "Test 3 done. Press Enter for Test 4 (degrade)…" _

run_step 04-degrade-whep.sh

echo ""
echo "All 4 tests finished. Reports under load-test/reports/"
