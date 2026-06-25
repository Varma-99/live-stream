#!/usr/bin/env bash
# 05 — SRS 2-tier: origin API, playback forward, WHEP 201.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${DIR}/../.." && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="05-whep-2tier"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

STREAM_KEY="${STREAM_KEY:-clustertest}"
MIN_SDP="${ROOT}/load-test/fixtures/min-whep-offer.sdp"
PUBLISH_PID=""

cleanup() {
  if [[ -n "${PUBLISH_PID}" ]] && kill -0 "${PUBLISH_PID}" 2>/dev/null; then
    kill "${PUBLISH_PID}" 2>/dev/null || true
    wait "${PUBLISH_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

[[ -f "${MIN_SDP}" ]] || cluster_fail "missing ${MIN_SDP}"

echo "=== ${TEST_ID} SRS 2-tier WHEP ==="
"${ROOT}/scripts/publish-srs-test.sh" "${STREAM_KEY}" &
PUBLISH_PID=$!
sleep 4

curl -sf "${CLUSTER_ORIGIN_API}/api/v1/streams/" | python3 -c "
import json,sys
d=json.load(sys.stdin)
keys=[s.get('name','') for s in d.get('streams',[])]
if not any('${STREAM_KEY}' in k for k in keys):
    print('origin missing stream', keys); sys.exit(1)
print('origin OK')
" || cluster_fail "stream not on origin API"

curl -sf "${CLUSTER_PLAYBACK_API}/api/v1/streams/" | python3 -c "
import json,sys
d=json.load(sys.stdin)
keys=[s.get('name','') for s in d.get('streams',[])]
if not any('${STREAM_KEY}' in k for k in keys):
    print('playback missing stream', keys); sys.exit(1)
print('playback OK (forward)')
" || cluster_fail "stream not on playback API"

WHEP_URL="${CLUSTER_WHEP_LB}/rtc/v1/whep/?app=live&stream=${STREAM_KEY}"
HTTP_CODE="$(curl -sS -o "${REPORTS_DIR}/whep-answer.sdp" -w '%{http_code}' \
  -X POST "${WHEP_URL}" \
  -H 'Content-Type: application/sdp' \
  --data-binary "@${MIN_SDP}" \
  --max-time 20)"
echo "WHEP HTTP ${HTTP_CODE}"
[[ "${HTTP_CODE}" == "201" || "${HTTP_CODE}" == "200" ]] || cluster_fail "WHEP handshake failed"

cluster_pass "origin → playback forward → WHEP LB"
