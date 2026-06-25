#!/usr/bin/env bash
# Multi-instance cluster test suite (Phases 6–8 + resilience).
# Profiles: quick (default) | ci | full
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
PROFILE="${PROFILE:-quick}"

export BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
SKIP_TESTS="${SKIP_TESTS:-}"

case "${PROFILE}" in
  quick) TESTS=(00-preflight.sh 01-lb-lifecycle.sh 02-cross-instance-broadcaster.sh 03-cross-instance-stop.sh 04-viewer-presence.sh 06-lb-round-robin.sh) ;;
  ci)    TESTS=(00-preflight.sh 01-lb-lifecycle.sh 02-cross-instance-broadcaster.sh 03-cross-instance-stop.sh 04-viewer-presence.sh 05-whep-2tier.sh 06-lb-round-robin.sh 07-redis-restart-grace.sh) ;;
  full)  TESTS=(00-preflight.sh 01-lb-lifecycle.sh 02-cross-instance-broadcaster.sh 03-cross-instance-stop.sh 04-viewer-presence.sh 05-whep-2tier.sh 06-lb-round-robin.sh 07-redis-restart-grace.sh 08-srs-playback-kill.sh 09-ffmpeg-watchdog.sh 10-k6-presence.sh) ;;
  *) echo "Unknown PROFILE=${PROFILE} (use quick|ci|full)" >&2; exit 1 ;;
esac

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Cluster suite  profile=${PROFILE}  run=${RUN_ID}              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Prerequisites:"
echo "  colima start --port-forwarder=grpc"
echo "  docker-compose -f docker-compose.dev-infra.yml up -d"
echo "  SRS_STACK=2tier ./scripts/start-srs.sh"
echo "  app-1: ./mvnw -Dexec.args=\"server config/config-dev-pg-app1.yml\" compile exec:java"
echo "  app-2: ./mvnw -Dexec.args=\"server config/config-dev-pg-app2.yml\" compile exec:java"
echo "  ./scripts/start-app-lb.sh"
echo ""

FAILURES=0
run_test() {
  local script="$1"
  local id="${script%.sh}"
  if echo "${SKIP_TESTS}" | rg -q "${id}" 2>/dev/null; then
    echo "SKIP ${script}"
    return 0
  fi
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶ ${script}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  chmod +x "${DIR}/${script}"
  if "${DIR}/${script}"; then
    echo "✓ ${script}"
  else
    echo "✗ ${script} FAILED" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

for t in "${TESTS[@]}"; do
  run_test "${t}"
  if [[ "${t}" == "00-preflight.sh" && "${FAILURES}" -eq 0 ]]; then
    # shellcheck source=lib/common.sh
    source "${DIR}/lib/common.sh"
    cluster_cleanup_streams
  fi
done

chmod +x "${DIR}/generate-report.py"
python3 "${DIR}/generate-report.py" "${ROOT}/load-test/reports" "${RUN_ID}" "${PROFILE}"

echo ""
if [[ "${FAILURES}" -eq 0 ]]; then
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  Cluster suite finished — ALL PASSED                        ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
else
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  Cluster suite finished — ${FAILURES} test(s) FAILED          ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  exit 1
fi
echo ""
echo "Report: load-test/reports/cluster-run-${RUN_ID}/CLUSTER-SUITE-REPORT.md"
