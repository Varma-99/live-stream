#!/usr/bin/env bash
# SRS Phase 5 — full load suite (non-interactive). Saves reports under load-test/reports/.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
RUN_ID="$(date +%Y%m%d-%H%M%S)"

export MEDIA_BACKEND=srs

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  SRS Phase 5 load suite — backend=srs  run=${RUN_ID}       ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Prerequisites:"
echo "  1) colima start --port-forwarder=grpc"
echo "  2) ./scripts/start-srs.sh"
echo "  3) ./mvnw server config/config-dev.yml"
echo "  4) ONE live broadcast (broadcast.html → Start)"
echo ""
echo "Optional: ./load-test/srs-bench-suite/00-install-srs-bench.sh"
echo "Optional: brew install k6"
echo ""

SKIP_TESTS="${SKIP_TESTS:-}"
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
  "${DIR}/${script}" || {
    echo "WARN: ${script} exited non-zero — continuing suite" >&2
  }
}

run_test 00-preflight.sh

if ! curl -sf "${BASE_URL:-http://127.0.0.1:8080}/streams" | python3 -c "import sys,json; sys.exit(0 if json.load(sys.stdin) else 1)" 2>/dev/null; then
  echo ""
  echo "ERROR: No LIVE stream. Start broadcast, then re-run:"
  echo "  ./load-test/srs-suite/run-all.sh"
  exit 1
fi

# k6 load can starve broadcast.html heartbeats → zombie stop after 15s. Keep alive via curl.
KEEPALIVE_PID=""
stop_keepalive() {
  if [[ -n "${KEEPALIVE_PID}" ]]; then
    kill "${KEEPALIVE_PID}" 2>/dev/null || true
    wait "${KEEPALIVE_PID}" 2>/dev/null || true
    KEEPALIVE_PID=""
  fi
}
trap stop_keepalive EXIT
# shellcheck source=load-test/lib/common.sh
source "${ROOT}/load-test/lib/common.sh"
load_live_stream
chmod +x "${ROOT}/load-test/lib/keep-broadcast-alive.sh"
"${ROOT}/load-test/lib/keep-broadcast-alive.sh" &
KEEPALIVE_PID=$!
echo "Broadcaster keepalive running (pid ${KEEPALIVE_PID}) for stream ${STREAM_ID}"
echo "Tip: leave broadcast.html open for FFmpeg; heartbeats are covered by keepalive during the suite."
echo ""

# Full suite order (no 200-viewer k6 — 600 only):
#   00 preflight → 01 baseline → 03 k6 600 viewers → 04–09 load/degrade → report
run_test 01-baseline-qos.sh
run_test 03-k6-viewers-600.sh
run_test 04-k6-whep-handshake.sh
run_test 05-rtmp-many-publishers.sh
run_test 06-rtmp-many-subscribers.sh
run_test 07-multi-path-abr.sh
run_test 08-whep-srs-bench.sh
run_test 09-degrade-under-load.sh

chmod +x "${DIR}/generate-report.py"
python3 "${DIR}/generate-report.py" "${ROOT}/load-test/reports" "${RUN_ID}"

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  SRS Phase 5 suite finished                                   ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Report: load-test/reports/srs-phase5-run-${RUN_ID}/SRS-PHASE5-REPORT.md"
echo "Per-test dirs: load-test/reports/srs-suite-*"
echo ""
echo "Manual: screenshot war room during 01-baseline for your write-up."
