#!/usr/bin/env bash
# POST /streams/{id}/broadcaster-heartbeat every few seconds so load tests do not
# zombie-stop FFmpeg when the broadcast.html tab is throttled under k6 load.
#
# Usage (background for a suite):
#   source load-test/lib/common.sh && load_live_stream
#   ./load-test/lib/keep-broadcast-alive.sh &
#   KEEPALIVE_PID=$!
#   trap 'kill "$KEEPALIVE_PID" 2>/dev/null || true' EXIT
#
# The browser tab still starts the show; this only replaces heartbeat POSTs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=load-test/lib/common.sh
source "${ROOT}/load-test/lib/common.sh"

INTERVAL_SEC="${BROADCASTER_HEARTBEAT_INTERVAL_SEC:-4}"

if [[ -z "${STREAM_ID:-}" ]]; then
  load_live_stream || exit 1
fi

echo "Broadcaster keepalive: stream ${STREAM_ID} every ${INTERVAL_SEC}s → ${BASE_URL}" >&2

failures=0
while true; do
  if curl -sf -X POST "${BASE_URL}/streams/${STREAM_ID}/broadcaster-heartbeat" >/dev/null; then
    failures=0
  else
    failures=$((failures + 1))
    if (( failures == 1 || failures % 5 == 0 )); then
      echo "WARN: broadcaster-heartbeat failed for stream ${STREAM_ID} (${failures} in a row)" >&2
    fi
    # Stream may have ended intentionally; stop when gone from /streams.
    if ! curl -sf "${BASE_URL}/streams" | python3 -c "
import json, sys
live = json.load(sys.stdin)
sys.exit(0 if any(str(s.get('id')) == '${STREAM_ID}' for s in live) else 1)
" 2>/dev/null; then
      echo "Broadcaster keepalive: stream ${STREAM_ID} no longer LIVE — exiting" >&2
      exit 0
    fi
  fi
  sleep "${INTERVAL_SEC}"
done
