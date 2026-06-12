#!/usr/bin/env bash
# Monitor app ABR (_high / _mid / _low) on SRS while FFmpeg publishes all rungs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"

REPORTS_DIR="$(report_dir 07-multi-path-abr)"
SAMPLES="${ABR_SAMPLE_COUNT:-12}"
INTERVAL_SEC="${ABR_SAMPLE_INTERVAL_SEC:-10}"
require_srs
require_live_stream
suite_meta

echo "=== 07 ABR path monitor (${SAMPLES} samples × ${INTERVAL_SEC}s) ==="
echo "Expect 3 SRS paths: ${STREAM_KEY}_high _mid _low"
echo "Reports: ${REPORTS_DIR}"

for i in $(seq 1 "${SAMPLES}"); do
  save_srs_streams "sample-${i}"
  save_qos_snapshot "sample-${i}"
  sleep "${INTERVAL_SEC}"
done

python3 - <<'PY' "${REPORTS_DIR}" "${STREAM_KEY}" > "${REPORTS_DIR}/abr-summary.txt"
import json, sys, glob, os
report_dir, stream_key = sys.argv[1], sys.argv[2]
paths = {f"{stream_key}_high", f"{stream_key}_mid", f"{stream_key}_low"}
found = {p: [] for p in paths}
for f in sorted(glob.glob(os.path.join(report_dir, "srs-streams-sample-*.json"))):
    data = json.load(open(f))
    names = {s.get("name") for s in data.get("streams", [])}
    for p in paths:
        if p in names:
            found[p].append(os.path.basename(f))
print("ABR path presence across samples:")
for p, samples in found.items():
    print(f"  {p}: seen in {len(samples)}/{len(glob.glob(os.path.join(report_dir, 'srs-streams-sample-*.json')))} snapshots")
PY

save_postmortem after-abr
echo "07 complete. See abr-summary.txt"
