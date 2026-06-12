#!/usr/bin/env bash
# Print the current LIVE stream id + key (k6 / srs-bench suite).
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
curl -sf "${BASE_URL}/streams" | python3 -c "
import sys, json
live = json.load(sys.stdin)
if not live:
    print('No LIVE stream. Start one on the broadcast page first.', file=sys.stderr)
    sys.exit(1)
for s in live:
    key = s.get('streamKey') or ''
    print(f\"id={s['id']}  key={key}  title={s.get('title','')}\")
s = live[0]
print(f\"k6:       k6 run --env STREAM_ID={s['id']} load-test/k6-viewer-qos.js\")
print(f\"SRS suite: ./load-test/srs-suite/run-all.sh\")
if s.get('streamKey'):
    print(f\"RTMP high: rtmp://127.0.0.1:1935/live/{s['streamKey']}_high\")
    print(f\"WHEP proxy: {sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8080'}/whep/live/{s['streamKey']}_high/whep\")
" "${BASE_URL}"
