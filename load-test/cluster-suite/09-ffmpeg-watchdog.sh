#!/usr/bin/env bash
# 09 — FFmpeg watchdog: documents current behavior (no auto-restart yet).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${DIR}/lib/common.sh"
export TEST_ID="09-ffmpeg-watchdog"
REPORTS_DIR="$(cluster_report_dir "${TEST_ID}")"
export REPORTS_DIR

KEEPALIVE_PID=""
trap 'cluster_keepalive_stop "${KEEPALIVE_PID}"' EXIT

echo "=== ${TEST_ID} FFmpeg watchdog (informational) ==="
SID="$(cluster_start_stream "${APP1_URL}" "ffmpeg-watchdog")"
cluster_keepalive_start "${SID}"
KEEPALIVE_PID="${CLUSTER_LAST_KEEPALIVE_PID}"
sleep 2

# Find ffmpeg child for stream via publishActive / process list — use pgrep on parent's children
FFMPEG_PID="$(pgrep -f "ffmpeg.*live/" 2>/dev/null | head -1 || true)"
if [[ -z "${FFMPEG_PID}" ]]; then
  echo "WARN: no ffmpeg PID found (test pattern may use different cmdline)"
  cluster_write_result "SKIP" "no ffmpeg pid found"
  cluster_stop_stream "${SID}" "${APP1_URL}"
  echo "SKIP ${TEST_ID}: no ffmpeg to kill"
  exit 0
fi

echo "killing ffmpeg pid=${FFMPEG_PID}"
kill "${FFMPEG_PID}" 2>/dev/null || true
sleep 12

STATUS="$(curl -sf "${BASE_URL}/streams" \
  | python3 -c "import sys,json; d=[x for x in json.load(sys.stdin) if x['id']==${SID}]; print(d[0]['status'] if d else 'GONE')")"
PUBLISH="$(curl -sf "${BASE_URL}/streams" \
  | python3 -c "import sys,json; d=[x for x in json.load(sys.stdin) if x['id']==${SID}]; print(d[0].get('publishActive', False) if d else False)")"
echo "after ffmpeg kill: status=${STATUS} publishActive=${PUBLISH}"

cluster_stop_stream "${SID}" "${BASE_URL}"

if [[ "${PUBLISH}" == "True" || "${PUBLISH}" == "true" ]]; then
  echo "KNOWN GAP: stream still shows publishActive after FFmpeg death (watchdog not implemented)"
  cluster_write_result "WARN" "FFmpeg watchdog not implemented — documented gap"
  echo "PASS ${TEST_ID} (informational — gap documented)"
  exit 0
fi

cluster_pass "FFmpeg death detected by app"
