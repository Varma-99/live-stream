#!/usr/bin/env bash
# Many concurrent RTMP publishers → SRS (bench paths, not app's stream key).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 05-rtmp-many-publishers)"
PUBLISHERS="${RTMP_PUBLISHER_COUNT:-20}"
PUBLISH_SEC="${RTMP_PUBLISH_SEC:-90}"
require_srs
require_live_stream
suite_meta

PUBLISH_BIN="$(sb_rtmp_publish_bin)" || {
  echo "ERROR: sb_rtmp_publish missing. Run: ./load-test/srs-bench-suite/00-install-srs-bench.sh" >&2
  exit 1
}
PUBLISH_FLV="$(bench_publish_flv "${ROOT}")" || exit 1

echo "=== 05 ${PUBLISHERS} RTMP publishers → SRS (${PUBLISH_SEC}s) ==="
echo "Loop FLV: ${PUBLISH_FLV}"
echo "App stream ${STREAM_KEY} continues; bench uses bench1..bench${PUBLISHERS}"
echo "Reports: ${REPORTS_DIR}"

save_srs_streams before-publishers

LIST="${REPORTS_DIR}/publishers.txt"
: > "${LIST}"
for i in $(seq 1 "${PUBLISHERS}"); do
  printf '%s\n' "$(rtmp_publish_url "" "bench${i}")" >> "${LIST}"
done

pids=()
for i in $(seq 1 "${PUBLISHERS}"); do
  url="$(sed -n "${i}p" "${LIST}")"
  "${PUBLISH_BIN}" -i "${PUBLISH_FLV}" -c 1 -r "${url}" >> "${REPORTS_DIR}/publish-${i}.log" 2>&1 &
  pids+=($!)
done

echo "Started ${#pids[@]} publishers. Holding ${PUBLISH_SEC}s…"
sleep "${PUBLISH_SEC}"

for pid in "${pids[@]}"; do
  kill "${pid}" 2>/dev/null || true
done
wait 2>/dev/null || true

save_srs_streams after-publishers
save_qos_snapshot after-publishers
save_postmortem after-publishers

echo "05 complete. Active streams in srs-streams-after-publishers.json"
