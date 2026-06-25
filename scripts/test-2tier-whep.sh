#!/usr/bin/env bash
# Phase 2 gate: publish RTMP to origin, WHEP handshake via HAProxy → playback tier.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/srs-docker.sh
source "${ROOT}/scripts/lib/srs-docker.sh"

STREAM_KEY="${1:-phase2test}"
MIN_SDP="${ROOT}/load-test/fixtures/min-whep-offer.sdp"
PUBLISH_PID=""

cleanup() {
  if [[ -n "${PUBLISH_PID}" ]] && kill -0 "${PUBLISH_PID}" 2>/dev/null; then
    kill "${PUBLISH_PID}" 2>/dev/null || true
    wait "${PUBLISH_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if ! srs_2tier_running; then
  echo "ERROR: 2-tier SRS stack not running. Run: SRS_STACK=2tier ./scripts/start-srs.sh" >&2
  exit 1
fi
if [[ ! -f "${MIN_SDP}" ]]; then
  echo "ERROR: missing ${MIN_SDP}" >&2
  exit 1
fi

echo "Publishing test pattern to origin (background)…"
"${ROOT}/scripts/publish-srs-test.sh" "${STREAM_KEY}" &
PUBLISH_PID=$!
sleep 4

WHEP_URL="http://127.0.0.1:1985/rtc/v1/whep/?app=live&stream=${STREAM_KEY}"
echo "WHEP POST ${WHEP_URL}"
HTTP_CODE="$(curl -sS -o "${ROOT}/data/phase2-whep-answer.sdp" -w '%{http_code}' \
  -X POST "${WHEP_URL}" \
  -H 'Content-Type: application/sdp' \
  --data-binary "@${MIN_SDP}" \
  --max-time 20)"

echo "HTTP ${HTTP_CODE}"
if [[ "${HTTP_CODE}" == "201" || "${HTTP_CODE}" == "200" ]]; then
  echo "Phase 2 gate PASSED (WHEP handshake ok)"
  head -3 "${ROOT}/data/phase2-whep-answer.sdp" || true
  exit 0
fi

echo "Phase 2 gate FAILED (expected 200/201)" >&2
echo "  docker logs srs-playback-1 --tail 30" >&2
echo "  docker logs srs-origin --tail 30" >&2
exit 1
