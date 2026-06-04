#!/usr/bin/env bash
# Test 3: Many RTMP publishers to MediaMTX (paths separate from app stream key).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"

REPORTS_DIR="$(report_dir 03-rtmp-many)"
PUBLISHERS="${RTMP_PUBLISHER_COUNT:-15}"
PUBLISH_SEC="${RTMP_PUBLISH_SEC:-60}"
require_live_stream

PUBLISH_BIN="$(sb_rtmp_publish_bin)" || {
  echo "ERROR: sb_rtmp_publish missing. Run 00-install-srs-bench.sh" >&2
  exit 1
}

echo "=== Test 3: ${PUBLISHERS} RTMP publishers (${PUBLISH_SEC}s) ==="
echo "Uses bench paths (NOT ${STREAM_KEY}) so app FFmpeg is not fought."
echo "Reports: ${REPORTS_DIR}"

LIST="${REPORTS_DIR}/publishers.txt"
: > "${LIST}"
for i in $(seq 1 "${PUBLISHERS}"); do
  printf '%s\n' "$(rtmp_publish_url _high "bench${i}")" >> "${LIST}"
done

echo "Publisher URLs → ${LIST}"
curl -sf "http://127.0.0.1:9997/v3/paths/list" -o "${REPORTS_DIR}/mediamtx-paths-before.json" 2>/dev/null || true

pids=()
for i in $(seq 1 "${PUBLISHERS}"); do
  url="$(sed -n "${i}p" "${LIST}")"
  "${PUBLISH_BIN}" -c 1 -r "${url}" >> "${REPORTS_DIR}/publish-${i}.log" 2>&1 &
  pids+=($!)
done

echo "Started ${#pids[@]} publishers. Sleeping ${PUBLISH_SEC}s…"
sleep "${PUBLISH_SEC}"

for pid in "${pids[@]}"; do
  kill "${pid}" 2>/dev/null || true
done
wait 2>/dev/null || true

curl -sf "http://127.0.0.1:9997/v3/paths/list" -o "${REPORTS_DIR}/mediamtx-paths-after.json" 2>/dev/null || true

save_qos_snapshot after-rtmp-many
save_postmortem after-rtmp-many

echo "Test 3 complete. Check publish-*.log and mediamtx-paths-*.json"
