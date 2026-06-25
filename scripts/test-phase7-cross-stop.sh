#!/usr/bin/env bash
# Phase 7 gate: start on app-1, stop via app-2 (cross-instance forward).
# Requires:
#   docker-compose -f docker-compose.dev-infra.yml up -d
#   ./mvnw -Dexec.args="server config/config-dev-pg-app1.yml" compile exec:java   (terminal 1)
#   ./mvnw -Dexec.args="server config/config-dev-pg-app2.yml" compile exec:java   (terminal 2)
set -euo pipefail

APP1="${APP1_URL:-http://127.0.0.1:8090}"
APP2="${APP2_URL:-http://127.0.0.1:8092}"
TOKEN="${INTERNAL_API_TOKEN:-dev-internal-token}"
DOCKER="${DOCKER:-/opt/homebrew/bin/docker}"

rcli() {
  "${DOCKER}" exec livestream-redis redis-cli "$@"
}

echo "=== Phase 7 cross-instance stop ==="
echo "app-1=${APP1}  app-2=${APP2}"

echo -n "app-1 health: "
curl -sf "${APP1}/internal/health" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['instanceId'], 'ok' if d.get('healthy') else 'FAIL')"

echo -n "app-2 health: "
curl -sf "${APP2}/internal/health" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['instanceId'], 'ok' if d.get('healthy') else 'FAIL')"

echo ""
echo "Starting stream on app-1..."
STREAM_ID="$(
  curl -sf -X POST "${APP1}/streams/start" \
    -H 'Content-Type: application/json' \
    -d '{"broadcasterId":1,"title":"phase7-cross-stop","delivery":"webrtc"}' \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
)"
echo "stream id=${STREAM_ID}"

OWNER="$(rcli GET "stream:owner:${STREAM_ID}")"
echo "redis stream:owner = ${OWNER} (expect app-1)"
if [[ "${OWNER}" != "app-1" ]]; then
  echo "FAIL: unexpected owner" >&2
  exit 1
fi

echo ""
echo "Stopping stream via app-2 (should forward to app-1)..."
STATUS="$(
  curl -sf -X POST "${APP2}/streams/${STREAM_ID}/stop" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])"
)"
echo "stop response status=${STATUS}"

REDIS_STATUS="$(rcli GET "stream:status:${STREAM_ID}")"
echo "redis stream:status = ${REDIS_STATUS}"

if [[ "${STATUS}" == "ENDED" && "${REDIS_STATUS}" == "ENDED" ]]; then
  echo ""
  echo "PASS Phase 7 — cross-instance stop"
else
  echo "FAIL Phase 7" >&2
  exit 1
fi
