#!/usr/bin/env bash
# Phase 8 gate: control plane through HAProxy on :8080 (start, heartbeat, stop).
# Requires:
#   docker-compose -f docker-compose.dev-infra.yml up -d
#   app-1 + app-2 running (config-dev-pg-app1.yml / app2.yml)
#   ./scripts/start-app-lb.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/app-cluster.sh
source "${ROOT}/scripts/lib/app-cluster.sh"

cd "${ROOT}"
BASE="${BASE_URL:-${APP_LB_URL}}"

echo "=== Phase 8 app LB gate ==="
echo "BASE_URL=${BASE}"

if ! app_cluster_http_ok "${BASE}/internal/health"; then
  echo "FAIL: LB not reachable at ${BASE}" >&2
  echo "Run: ./scripts/start-app-lb.sh (and both app instances)" >&2
  exit 1
fi

echo ""
echo "--- LB round-robin (internal/health) ---"
SEEN=""
for _ in 1 2 3 4 5 6; do
  ID="$(app_cluster_instance_health "${BASE}")"
  echo "  hit → ${ID}"
  SEEN="${SEEN} ${ID}"
done
if [[ "${SEEN}" != *"app-1"* || "${SEEN}" != *"app-2"* ]]; then
  echo "WARN: expected both app-1 and app-2 in LB rotation (got:${SEEN})" >&2
fi

echo ""
echo "--- Start stream via LB ---"
START_BODY="$(curl -sf -X POST "${BASE}/streams/start" \
  -H 'Content-Type: application/json' \
  -d '{"broadcasterId":1,"title":"phase8-lb","delivery":"webrtc"}')"
STREAM_ID="$(printf '%s' "${START_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")"
echo "stream id=${STREAM_ID}"

OWNER="$(app_cluster_rcli GET "stream:owner:${STREAM_ID}")"
echo "redis stream:owner = ${OWNER}"
if [[ "${OWNER}" != "app-1" && "${OWNER}" != "app-2" ]]; then
  echo "FAIL: missing or invalid owner" >&2
  exit 1
fi

echo ""
echo "--- Broadcaster heartbeat via LB (3x) ---"
for _ in 1 2 3; do
  curl -sf -o /dev/null -w "  heartbeat HTTP %{http_code}\n" \
    -X POST "${BASE}/streams/${STREAM_ID}/broadcaster-heartbeat"
  sleep 1
done
HB_TTL="$(app_cluster_rcli TTL "broadcaster:heartbeat:${STREAM_ID}")"
echo "heartbeat TTL=${HB_TTL} (expect > 0)"

echo ""
echo "--- Stream still LIVE via LB ---"
LIVE_STATUS="$(curl -sf "${BASE}/streams" \
  | python3 -c "import sys,json; d=[x for x in json.load(sys.stdin) if x['id']==${STREAM_ID}]; print(d[0]['status'] if d else 'NOT_LISTED')")"
echo "list status=${LIVE_STATUS}"
if [[ "${LIVE_STATUS}" != "LIVE" ]]; then
  echo "FAIL: stream not LIVE after heartbeat" >&2
  exit 1
fi

echo ""
echo "--- Stop stream via LB (may forward to owner ${OWNER}) ---"
STOP_STATUS="$(curl -sf -X POST "${BASE}/streams/${STREAM_ID}/stop" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")"
echo "stop response status=${STOP_STATUS}"

REDIS_STATUS="$(app_cluster_rcli GET "stream:status:${STREAM_ID}")"
echo "redis stream:status = ${REDIS_STATUS}"

if [[ "${STOP_STATUS}" == "ENDED" && "${REDIS_STATUS}" == "ENDED" ]]; then
  echo ""
  echo "PASS Phase 8 — app LB (start, heartbeat, stop via :8080)"
else
  echo "FAIL Phase 8" >&2
  exit 1
fi
