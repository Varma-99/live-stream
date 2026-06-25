#!/usr/bin/env bash
# 08 — SRS playback kill: forward SPOF (document expected breakage).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="08-srs-playback-kill"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

STREAM_KEY="${STREAM_KEY:-playbackkill}"
PUBLISH_PID=""

cleanup() {
  if [[ -n "${PUBLISH_PID}" ]] && kill -0 "${PUBLISH_PID}" 2>/dev/null; then
    kill "${PUBLISH_PID}" 2>/dev/null || true
    wait "${PUBLISH_PID}" 2>/dev/null || true
  fi
  "${DOCKER}" start srs-playback-1 2>/dev/null || true
}
trap cleanup EXIT

echo "=== ${TEST_ID} SRS playback kill (SPOF) ==="
"${ROOT}/scripts/publish-srs-test.sh" "${STREAM_KEY}" &
PUBLISH_PID=$!
sleep 4

curl -sf "${CLUSTER_PLAYBACK_API}/api/v1/streams/" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert any('${STREAM_KEY}' in s.get('name','') for s in d.get('streams',[])), 'no stream before kill'
print('playback has stream before kill')
"

echo "stopping srs-playback-1..."
"${DOCKER}" stop srs-playback-1 >/dev/null
sleep 3

if curl -sf "${CLUSTER_PLAYBACK_API}/api/v1/streams/" 2>/dev/null | python3 -c "
import json,sys
d=json.load(sys.stdin)
keys=[s.get('name','') for s in d.get('streams',[])]
import sys
sys.exit(0 if any('${STREAM_KEY}' in k for k in keys) else 1)
" 2>/dev/null; then
  cluster_fail "expected stream gone from playback after kill"
fi
echo "playback API empty/down as expected (SPOF)"

echo "restarting playback..."
"${DOCKER}" start srs-playback-1 >/dev/null
sleep 5
echo "NOTE: forward may not auto-reconnect — republish may be required (known SRS limitation)"

cluster_pass "playback kill breaks forward (documented SPOF)"
