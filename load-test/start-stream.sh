#!/usr/bin/env bash
# Start a live stream for k6 load testing. Requires app on :8080.
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BROADCASTER_ID="${BROADCASTER_ID:-1}"
TITLE="${TITLE:-k6 Load Test Stream}"
DELIVERY="${DELIVERY:-auto}"

existing="$(curl -sf "${BASE_URL}/streams" 2>/dev/null || echo '[]')"
count="$(echo "$existing" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)"
if [[ "$count" != "0" ]]; then
  echo "A LIVE stream already exists (only one allowed with laptop camera):"
  echo "$existing" | python3 -m json.tool 2>/dev/null || echo "$existing"
  echo ""
  echo "Either:"
  echo "  1) Stop it on the broadcast page, then re-run this script, OR"
  echo "  2) Skip this script and run k6 (it auto-picks the live id):"
  echo "     k6 run load-test/k6-viewer-qos.js"
  exit 1
fi

resp="$(curl -sf -X POST "${BASE_URL}/streams/start" \
  -H 'Content-Type: application/json' \
  -d "{\"broadcasterId\":${BROADCASTER_ID},\"title\":\"${TITLE}\",\"delivery\":\"${DELIVERY}\"}")"

stream_id="$(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null || true)"
if [[ -z "${stream_id}" ]]; then
  echo "$resp"
  echo "Could not parse stream id — copy id from JSON above."
  exit 1
fi

echo "Stream started: id=${stream_id}"
echo "Run k6 with:"
echo "  k6 run --env BASE_URL=${BASE_URL} --env STREAM_ID=${stream_id} load-test/k6-viewer-qos.js"
