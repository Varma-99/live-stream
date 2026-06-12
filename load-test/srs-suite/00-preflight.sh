#!/usr/bin/env bash
# Verify SRS + app + Colima + optional k6/srs-bench before the suite.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 00-preflight)"
suite_meta

echo "=== SRS Phase 5 preflight ==="
echo "Reports: ${REPORTS_DIR}"

FAIL=0

check() {
  local name="$1"
  shift
  if "$@"; then
    echo "  OK   ${name}"
  else
    echo "  FAIL ${name}"
    FAIL=1
  fi
}

check "SRS API :1985" curl -sf "${SRS_API_BASE}/api/v1/streams/" >/dev/null
check "SRS players :8088" curl -sf -o /dev/null "http://127.0.0.1:8088/players/whep.html"
check "App :8080" require_app
check "k6 installed" command -v k6 >/dev/null 2>&1
check "sb_rtmp_publish" sb_rtmp_publish_bin >/dev/null
check "sb_rtmp_load" sb_rtmp_load_bin >/dev/null

require_colima_grpc || true

if load_live_stream 2>/dev/null; then
  echo "  OK   LIVE stream id=${STREAM_ID} key=${STREAM_KEY}"
  save_qos_snapshot preflight
  save_srs_streams preflight
else
  echo "  WARN no LIVE stream (start broadcast before load tests)"
fi

{
  echo "colima_portForwarder=$(awk '/^portForwarder:/ {print $2}' "${HOME}/.colima/default/colima.yaml" 2>/dev/null || echo unknown)"
  echo "lan_ip=$(ipconfig getifaddr en0 2>/dev/null || echo unknown)"
  curl -sf "${BASE_URL}/config/public" 2>/dev/null || echo "config_public=unavailable"
} > "${REPORTS_DIR}/environment.txt"

if [[ "${FAIL}" -ne 0 ]]; then
  echo ""
  echo "Preflight FAILED. Fix issues above before run-all.sh"
  exit 1
fi

echo ""
echo "Preflight passed."
