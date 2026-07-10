#!/usr/bin/env bash
# SRS edge cluster validation suite (origin + edges + HAProxy).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
RUN_ID="$(date +%Y%m%d-%H%M%S)"

export MEDIA_BACKEND=srs
export BASE_URL="${BASE_URL:-http://10.255.45.175:8080}"
export CLUSTER_ORIGIN_API="${CLUSTER_ORIGIN_API:-http://127.0.0.1:1995}"
export CLUSTER_WHEP_LB="${CLUSTER_WHEP_LB:-http://127.0.0.1:1985}"

# shellcheck source=load-test/lib/common.sh
source "${ROOT}/load-test/lib/common.sh"
# shellcheck source=load-test/lib/srs-cluster.sh
source "${ROOT}/load-test/lib/srs-cluster.sh"
apply_cluster_env

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  SRS edge cluster suite — run=${RUN_ID}                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Prerequisites:"
echo "  1) colima start --port-forwarder=grpc"
echo "  2) SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh"
echo "  3) ./mvnw server config/config-dev-cluster.yml"
echo "  4) ONE live stream OR suite will start a dummy streamer"
echo ""
echo "BASE_URL=${BASE_URL}"
echo "Origin API=${CLUSTER_ORIGIN_API}  WHEP LB=${CLUSTER_WHEP_LB}"
echo ""

SKIP_TESTS="${SKIP_TESTS:-}"
FAILURES=0

run_test() {
  local script="$1"
  local id="${script%.sh}"
  if echo "${SKIP_TESTS}" | rg -q "${id}"; then
    echo "SKIP ${script} (SKIP_TESTS)"
    return 0
  fi
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶ ${script}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  chmod +x "${DIR}/${script}"
  if "${DIR}/${script}"; then
    echo "PASS ${script}"
  else
    echo "FAIL ${script}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

run_test 00-preflight-cluster.sh

ensure_live_stream_or_dummy

KEEPALIVE_PID=""
stop_keepalive() {
  if [[ -n "${KEEPALIVE_PID}" ]]; then
    kill "${KEEPALIVE_PID}" 2>/dev/null || true
    wait "${KEEPALIVE_PID}" 2>/dev/null || true
    KEEPALIVE_PID=""
  fi
}
trap stop_keepalive EXIT

if [[ "$(stream_is_dummy)" != "true" ]]; then
  chmod +x "${ROOT}/load-test/lib/keep-broadcast-alive.sh"
  "${ROOT}/load-test/lib/keep-broadcast-alive.sh" &
  KEEPALIVE_PID=$!
  echo "Broadcaster keepalive pid ${KEEPALIVE_PID} (camera stream)"
else
  echo "Dummy stream — no broadcaster keepalive needed"
fi
echo ""

run_test 10-edge-failover.sh
run_test 11-origin-edge-ttff.sh
run_test 12-pull-efficiency.sh

chmod +x "${DIR}/generate-report.py"
python3 "${DIR}/generate-report.py" "${ROOT}/load-test/reports" "${RUN_ID}" cluster

echo ""
if [[ "${FAILURES}" -eq 0 ]]; then
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  SRS cluster suite finished — ALL PASSED                      ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
else
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  SRS cluster suite finished — ${FAILURES} test(s) FAILED       ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  exit 1
fi
echo ""
echo "Report: load-test/reports/srs-cluster-run-${RUN_ID}/SRS-CLUSTER-REPORT.md"
echo "Per-test: load-test/reports/srs-cluster-suite-*"
