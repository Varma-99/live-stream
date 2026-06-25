#!/usr/bin/env bash
# SRS 6 serves WHEP on origin (RTC disabled on RTMP edges). Kill origin; verify WHEP recovers.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "${ROOT}/load-test/lib/common.sh"
source "${ROOT}/load-test/lib/srs.sh"
source "${ROOT}/load-test/lib/srs-cluster.sh"

apply_cluster_env
REPORTS_DIR="$(report_dir_cluster 10-edge-failover)"
VUS_BEFORE="${FAILOVER_VUS_BEFORE:-30}"
VUS_AFTER="${FAILOVER_VUS_AFTER:-30}"
HOLD_SEC="${FAILOVER_HOLD_SEC:-40}"
FAILOVER_WAIT_SEC="${FAILOVER_WAIT_SEC:-12}"
ORIGIN_RESTART_WAIT_SEC="${ORIGIN_RESTART_WAIT_SEC:-25}"

require_cluster_stack
ensure_live_stream_or_dummy
suite_meta_cluster

echo "=== 10 origin failover (WHEP via LB → origin, stream=${STREAM_KEY}) ==="
echo "Note: SRS 6 disables WebRTC on RTMP edges — WHEP terminates on origin."
echo "Reports: ${REPORTS_DIR}"

warm_stream_whep "${STREAM_KEY}"
sleep 2

if ! origin_has_stream "${STREAM_KEY}"; then
  echo "ERROR: ABR paths not visible on origin after warm-up" >&2
  exit 1
fi

save_origin_pull_stats before-failover
save_srs_streams before-failover

SUMMARY_BEFORE="${REPORTS_DIR}/k6-whep-before-failover.json"
echo "--- Phase A: WHEP load before kill (${VUS_BEFORE} VUs) ---"
run_k6_whep "${SUMMARY_BEFORE}" "${VUS_BEFORE}" 10 "${HOLD_SEC}" 8 \
  2>&1 | tee "${REPORTS_DIR}/k6-before-failover.log" || true

RATE_BEFORE="$(k6_handshake_ok_rate "${SUMMARY_BEFORE}")"
echo "WHEP ok rate before kill: ${RATE_BEFORE}"

echo "--- Phase B: kill ${CLUSTER_ORIGIN_NAME} ---"
docker kill "${CLUSTER_ORIGIN_NAME}" >/dev/null
echo "Killed ${CLUSTER_ORIGIN_NAME} at $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee "${REPORTS_DIR}/failover-event.txt"
sleep "${FAILOVER_WAIT_SEC}"

HANDSHAKE_DOWN="$(whep_handshake_once "${STREAM_KEY}_high" "${REPORTS_DIR}/handshake-while-down.sdp")"
echo "While origin down: ${HANDSHAKE_DOWN}" | tee -a "${REPORTS_DIR}/failover-event.txt"
DOWN_CODE="${HANDSHAKE_DOWN%% *}"

echo "--- Phase C: restart origin ---"
restart_origin_container
sleep "${ORIGIN_RESTART_WAIT_SEC}"
warm_stream_whep "${STREAM_KEY}" 5

HANDSHAKE_AFTER="$(whep_handshake_once "${STREAM_KEY}_high" "${REPORTS_DIR}/handshake-after-restart.sdp")"
echo "After origin restart: ${HANDSHAKE_AFTER}" | tee -a "${REPORTS_DIR}/failover-event.txt"
UP_CODE="${HANDSHAKE_AFTER%% *}"

SUMMARY_AFTER="${REPORTS_DIR}/k6-whep-after-failover.json"
echo "--- Phase D: WHEP load after restart (${VUS_AFTER} VUs) ---"
run_k6_whep "${SUMMARY_AFTER}" "${VUS_AFTER}" 10 "${HOLD_SEC}" 8 \
  2>&1 | tee "${REPORTS_DIR}/k6-after-failover.log" || true

RATE_AFTER="$(k6_handshake_ok_rate "${SUMMARY_AFTER}")"
echo "WHEP ok rate after restart: ${RATE_AFTER}"

save_srs_streams after-failover
save_postmortem after-failover

python3 - <<'PY' "${REPORTS_DIR}" "${RATE_BEFORE}" "${RATE_AFTER}" "${DOWN_CODE}" "${UP_CODE}"
import json, sys
report_dir, rb, ra, down, up = sys.argv[1:6]
rb_f = float(rb or 0)
ra_f = float(ra or 0)
pass_before = rb_f >= 0.85
pass_after = ra_f >= 0.70
pass_down = down not in ("200", "201")
pass_up = up in ("200", "201")
overall = pass_before and pass_after and pass_down and pass_up
summary = {
    "mode": "origin_whep_failover",
    "whep_ok_rate_before": rb_f,
    "whep_ok_rate_after_restart": ra_f,
    "handshake_while_origin_down": down,
    "handshake_after_origin_restart": up,
    "pass_before_threshold_0_85": pass_before,
    "pass_after_threshold_0_70": pass_after,
    "pass_unavailable_while_down": pass_down,
    "pass_recovered_after_restart": pass_up,
    "overall_pass": overall,
}
out = f"{report_dir}/failover-summary.json"
with open(out, "w") as f:
    json.dump(summary, f, indent=2)
print(json.dumps(summary, indent=2))
sys.exit(0 if overall else 1)
PY

echo "10 complete."
