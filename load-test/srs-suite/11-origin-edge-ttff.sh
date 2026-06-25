#!/usr/bin/env bash
# Measure cold publish→play and warm WHEP join latency on origin+edge cluster.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"
source "${ROOT}/load-test/lib/srs-cluster.sh"

apply_cluster_env
REPORTS_DIR="$(report_dir_cluster 11-origin-edge-ttff)"
COLD_KEY="ttff-$(date +%H%M%S)"
PARALLEL_JOINS="${TTFF_PARALLEL_JOINS:-20}"
PUBLISH_SEC="${TTFF_PUBLISH_SEC:-120}"

require_cluster_stack
ensure_live_stream_or_dummy
suite_meta_cluster

PUBLISH_BIN="$(sb_rtmp_publish_bin)" || {
  echo "ERROR: sb_rtmp_publish missing. Run: ./load-test/srs-bench-suite/00-install-srs-bench.sh" >&2
  exit 1
}
PUBLISH_FLV="$(bench_publish_flv "${ROOT}")" || exit 1

echo "=== 11 origin→edge TTFF ==="
echo "App stream (warm): ${STREAM_KEY}"
echo "Cold bench key:    ${COLD_KEY}"
echo "Reports: ${REPORTS_DIR}"

# --- Warm join on app ABR stream (edge cache likely hot) ---
warm_stream_on_edge "${STREAM_KEY}" 3 || true
WARM_RESULTS="${REPORTS_DIR}/warm-join-times.txt"
: > "${WARM_RESULTS}"
for rung in _high _mid _low; do
  result="$(whep_handshake_once "${STREAM_KEY}${rung}" "${REPORTS_DIR}/warm${rung}.sdp")"
  echo "${STREAM_KEY}${rung} ${result}" >> "${WARM_RESULTS}"
  sleep 1
done

# --- Parallel warm joins ---
PAR_FILE="${REPORTS_DIR}/parallel-join-times.txt"
: > "${PAR_FILE}"
for i in $(seq 1 "${PARALLEL_JOINS}"); do
  (
    r="$(whep_handshake_once "${STREAM_KEY}_high" "${REPORTS_DIR}/parallel-${i}.sdp")"
    echo "join-${i} ${r}" >> "${PAR_FILE}"
  ) &
done
wait

# --- Cold TTFF: new RTMP publish on origin until first successful WHEP via LB ---
COLD_RTMP="$(rtmp_publish_url _high "${COLD_KEY}")"
echo "Cold publish → ${COLD_RTMP}"
T0="$(python3 -c 'import time; print(f"{time.time():.3f}")')"
"${PUBLISH_BIN}" -i "${PUBLISH_FLV}" -c 1 -r "${COLD_RTMP}" >> "${REPORTS_DIR}/cold-publish.log" 2>&1 &
PUB_PID=$!

ORIGIN_READY=0
EDGE_READY=0
WHEP_CODE="000"
WHEP_SEC="0"
T_ORIGIN=""
T_EDGE=""
T_WHEP=""

deadline=$(( $(date +%s) + 90 ))
while (( $(date +%s) < deadline )); do
  if [[ "${ORIGIN_READY}" -eq 0 ]] && origin_path_active "${COLD_KEY}_high"; then
    T_ORIGIN="$(python3 -c 'import time; print(f"{time.time():.3f}")')"
    ORIGIN_READY=1
  fi
  if [[ "${EDGE_READY}" -eq 0 ]] && origin_has_stream "${COLD_KEY}"; then
    T_EDGE="$(python3 -c 'import time; print(f"{time.time():.3f}")')"
    EDGE_READY=1
  fi
  if [[ "${ORIGIN_READY}" -eq 1 ]]; then
    result="$(whep_handshake_once "${COLD_KEY}_high" "${REPORTS_DIR}/cold-whep.sdp")"
    code="${result%% *}"
    if [[ "${code}" == "200" || "${code}" == "201" ]]; then
      WHEP_CODE="${code}"
      WHEP_SEC="${result#* }"
      T_WHEP="$(python3 -c 'import time; print(f"{time.time():.3f}")')"
      break
    fi
  fi
  sleep 1
done

kill "${PUB_PID}" 2>/dev/null || true
wait "${PUB_PID}" 2>/dev/null || true

save_origin_pull_stats after-cold
save_srs_streams after-ttff

python3 - <<'PY' "${REPORTS_DIR}" "${T0}" "${T_ORIGIN}" "${T_EDGE}" "${T_WHEP}" \
  "${WHEP_CODE}" "${WHEP_SEC}" "${COLD_KEY}" "${STREAM_KEY}" "${WARM_RESULTS}" "${PAR_FILE}"
import json, statistics, sys
from pathlib import Path

(
    report_dir, t0, t_origin, t_edge, t_whep,
    whep_code, whep_sec, cold_key, app_key, warm_file, par_file,
) = sys.argv[1:12]
t0 = float(t0)

def delta(t):
    if not t:
        return None
    return round(float(t) - t0, 3)

warm = []
for line in Path(warm_file).read_text().splitlines():
    parts = line.split()
    if len(parts) >= 3:
        warm.append({"path": parts[0], "http": parts[1], "sec": float(parts[2])})

parallel = []
for line in Path(par_file).read_text().splitlines():
    parts = line.split()
    if len(parts) >= 3:
        parallel.append(float(parts[2]))

summary = {
    "cold_key": cold_key,
    "app_stream_key": app_key,
    "cold_origin_publish_sec": delta(t_origin),
    "cold_edge_visible_sec": delta(t_edge),
    "cold_first_whep_sec": delta(t_whep),
    "cold_whep_handshake_http": whep_code,
    "cold_whep_curl_sec": float(whep_sec or 0),
    "warm_abr_joins": warm,
    "parallel_join_count": len(parallel),
    "parallel_join_p50_sec": statistics.median(parallel) if parallel else None,
    "parallel_join_p95_sec": sorted(parallel)[int(0.95 * len(parallel)) - 1] if len(parallel) >= 2 else (parallel[0] if parallel else None),
    "pass_cold_whep": whep_code in ("200", "201"),
    "pass_cold_under_15s": delta(t_whep) is not None and delta(t_whep) <= 15.0,
}
out = Path(report_dir) / "ttff-summary.json"
out.write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
ok = summary["pass_cold_whep"] and summary["pass_cold_under_15s"]
sys.exit(0 if ok else 1)
PY

echo "11 complete."
