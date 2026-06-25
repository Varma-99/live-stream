#!/usr/bin/env bash
# 06 — LB round-robin hits both app-1 and app-2.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="06-lb-round-robin"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

echo "=== ${TEST_ID} LB round-robin ==="
SEEN=""
for _ in $(seq 1 12); do
  ID="$(app_cluster_instance_health "${BASE_URL}")"
  SEEN="${SEEN} ${ID}"
done
echo "hits:${SEEN}"

if [[ "${SEEN}" != *"app-1"* || "${SEEN}" != *"app-2"* ]]; then
  cluster_fail "did not hit both instances:${SEEN}"
fi

cluster_pass "LB routes to app-1 and app-2"
