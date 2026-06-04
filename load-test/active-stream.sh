#!/usr/bin/env bash
# Print the current LIVE stream id (for k6 STREAM_ID).
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
curl -sf "${BASE_URL}/streams" | python3 -c "
import sys, json
live = json.load(sys.stdin)
if not live:
    print('No LIVE stream. Start one on the broadcast page first.', file=sys.stderr)
    sys.exit(1)
for s in live:
    print(f\"id={s['id']}  title={s.get('title','')}\")
print(f\"Use: k6 run --env STREAM_ID={live[0]['id']} load-test/k6-viewer-qos.js\")
"
