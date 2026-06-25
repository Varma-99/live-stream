#!/usr/bin/env bash
# Prove origin RTMP pulls scale with paths×edges, not viewer count (ABR + multi-key).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"
source "${ROOT}/load-test/lib/srs-cluster.sh"

apply_cluster_env
REPORTS_DIR="$(report_dir_cluster 12-pull-efficiency)"
VIEWER_STEPS="${PULL_TEST_VIEWER_STEPS:-10 50 100}"
VUS_HOLD="${PULL_VUS_HOLD_SEC:-25}"
BENCH_KEYS="${PULL_BENCH_KEY_COUNT:-5}"
BENCH_SEC="${PULL_BENCH_SEC:-60}"

require_cluster_stack
ensure_live_stream_or_dummy
suite_meta_cluster

echo "=== 12 pull efficiency (ABR stream=${STREAM_KEY}) ==="
echo "Viewer steps: ${VIEWER_STEPS}"
echo "Bench keys:   ${BENCH_KEYS}"
echo "Reports: ${REPORTS_DIR}"

warm_stream_whep "${STREAM_KEY}"
sleep 2

save_origin_pull_stats abr-baseline
save_srs_streams abr-baseline

MATRIX="${REPORTS_DIR}/pull-matrix.jsonl"
: > "${MATRIX}"

# Phase A — scale WHEP handshakes; published ABR path count on origin stays at 3.
for vus in ${VIEWER_STEPS}; do
  save_origin_pull_stats "before-${vus}vu"
  summary="${REPORTS_DIR}/k6-pull-${vus}vu.json"
  echo "--- ${vus} WHEP VUs (${VUS_HOLD}s hold) ---"
  run_k6_whep "${summary}" "${vus}" 8 "${VUS_HOLD}" 6 \
    2>&1 | tee "${REPORTS_DIR}/k6-pull-${vus}vu.log" || true
  stats="$(origin_pull_stats)"
  rate="$(k6_handshake_ok_rate "${summary}")"
  [[ -z "${rate}" ]] && rate=0
  echo "{\"phase\":\"abr_scale\",\"vus\":${vus},\"k6_ok_rate\":${rate},\"stats\":${stats}}" >> "${MATRIX}"
  save_origin_pull_stats "after-${vus}vu"
done

# Phase B — multiple bench publishers (single _high path each).
PUBLISH_BIN="$(sb_rtmp_publish_bin)" || {
  echo "WARN: skipping multi-key phase (sb_rtmp_publish missing)" >&2
  BENCH_KEYS=0
}
if [[ "${BENCH_KEYS}" -gt 0 ]]; then
  PUBLISH_FLV="$(bench_publish_flv "${ROOT}")" || exit 1
  bench_pids=()
  for i in $(seq 1 "${BENCH_KEYS}"); do
    url="$(rtmp_publish_url _high "pull${i}")"
    "${PUBLISH_BIN}" -i "${PUBLISH_FLV}" -c 1 -r "${url}" >> "${REPORTS_DIR}/bench-pull-${i}.log" 2>&1 &
    bench_pids+=($!)
  done
  echo "Started ${BENCH_KEYS} bench publishers; waiting for origin…"
  sleep 8
  save_origin_pull_stats bench-multi-before-warm
  # One WHEP warm per key so edges pull from origin.
  for i in $(seq 1 "${BENCH_KEYS}"); do
    whep_handshake_once "pull${i}_high" "${REPORTS_DIR}/bench-warm-${i}.sdp" >/dev/null || true
    sleep 1
  done
  sleep 3
  stats="$(origin_pull_stats)"
  echo "{\"phase\":\"multi_key_bench\",\"keys\":${BENCH_KEYS},\"stats\":${stats}}" >> "${MATRIX}"
  save_origin_pull_stats bench-multi-after-warm
  for pid in "${bench_pids[@]}"; do
    kill "${pid}" 2>/dev/null || true
  done
  wait 2>/dev/null || true
fi

save_srs_streams after-pull-test
save_postmortem after-pull-test

python3 - <<'PY' "${REPORTS_DIR}" "${STREAM_KEY}" "${BENCH_KEYS}"
import json, sys
from pathlib import Path

report_dir, app_key, bench_keys = sys.argv[1:4]
bench_keys = int(bench_keys)
lines = Path(report_dir, "pull-matrix.jsonl").read_text().splitlines()
rows = [json.loads(l) for l in lines if l.strip()]

abr_rows = [r for r in rows if r.get("phase") == "abr_scale"]
pulls = [r["stats"]["edge_pull_clients"] for r in abr_rows if "stats" in r]
bench_row = next((r for r in rows if r.get("phase") == "multi_key_bench"), None)

abr_flat = False
if len(pulls) >= 2:
    abr_flat = (max(pulls) - min(pulls)) <= 2

abr_max_expected = 3  # three ABR publish paths on origin

bench_ok = True
bench_pulls = None
if bench_row:
    bench_pulls = bench_row["stats"]["edge_pull_clients"]
    # app ABR (~3 paths) + bench keys (≤1 publish path each)
    bench_max_expected = 3 + bench_keys + 3
    bench_ok = bench_pulls <= bench_max_expected

summary = {
    "app_stream_key": app_key,
    "mode": "origin_whep",
    "note": "SRS 6 disables WebRTC on RTMP edges; WHEP terminates on origin",
    "abr_viewer_steps": abr_rows,
    "abr_pull_clients_min": min(pulls) if pulls else None,
    "abr_pull_clients_max": max(pulls) if pulls else None,
    "abr_pulls_flat_across_viewers": abr_flat,
    "abr_pulls_within_expected": (max(pulls) if pulls else 0) <= abr_max_expected,
    "bench_keys": bench_keys,
    "bench_pull_clients": bench_pulls,
    "bench_pulls_within_expected": bench_ok,
    "overall_pass": abr_flat and (max(pulls) if pulls else 99) <= abr_max_expected and bench_ok,
}
out = Path(report_dir) / "pull-efficiency-summary.json"
out.write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
sys.exit(0 if summary["overall_pass"] else 1)
PY

echo "12 complete."
