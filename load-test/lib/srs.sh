# SRS-specific helpers. Source after common.sh:
#   source "${ROOT}/load-test/lib/common.sh"
#   source "${ROOT}/load-test/lib/srs.sh"

# SRS helpers — defaults match single-node stack.
# Edge cluster: export SRS_API_BASE=http://127.0.0.1:1995 SRS_WHEP_BASE=http://127.0.0.1:1985
SRS_API_BASE="${SRS_API_BASE:-http://127.0.0.1:1985}"
SRS_WHEP_BASE="${SRS_WHEP_BASE:-http://127.0.0.1:1985}"
SRS_FLV_BASE="${SRS_FLV_BASE:-http://127.0.0.1:8088}"
MEDIA_BACKEND="${MEDIA_BACKEND:-srs}"

require_srs() {
  curl -sf "${SRS_API_BASE}/api/v1/streams/" >/dev/null || {
    echo "ERROR: SRS API not reachable at ${SRS_API_BASE}. Run: ./scripts/start-srs.sh" >&2
    exit 1
  }
}

require_colima_grpc() {
  if command -v colima >/dev/null 2>&1 && colima status 2>&1 | rg -qi 'colima is running'; then
    local pf
    pf="$(awk '/^portForwarder:/ {print $2}' "${HOME}/.colima/default/colima.yaml" 2>/dev/null || true)"
    if [[ "${pf}" != "grpc" ]]; then
      echo "WARN: Colima portForwarder=${pf:-ssh} — WHEP/WebRTC may fail. Use: colima stop && colima start --port-forwarder=grpc" >&2
    fi
  fi
}

save_srs_streams() {
  local label="${1:-srs}"
  local out="${REPORTS_DIR}/srs-streams-${label}.json"
  if curl -sf "${SRS_API_BASE}/api/v1/streams/" -o "${out}"; then
    echo "Saved SRS streams → ${out}"
  fi
}

save_ops_qos() {
  local out="${REPORTS_DIR}/ops-qos.json"
  curl -sf "${BASE_URL}/streams/qos/ops" -o "${out}" 2>/dev/null && echo "Saved ops QoS → ${out}" || true
}

whep_url_direct() {
  local stream="${1:-${STREAM_KEY}_high}"
  echo "${SRS_WHEP_BASE}/rtc/v1/whep/?app=live&stream=${stream}"
}

whep_url_proxy() {
  local stream="${1:-${STREAM_KEY}_high}"
  echo "${WHEP_PROXY_BASE}/live/${stream}/whep"
}

srs_flv_url() {
  local stream="${1:-${STREAM_KEY}_high}"
  echo "${SRS_FLV_BASE}/live/${stream}.flv"
}

suite_meta() {
  cat > "${REPORTS_DIR}/suite-meta.json" <<EOF
{
  "backend": "${MEDIA_BACKEND}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "base_url": "${BASE_URL}",
  "srs_api": "${SRS_API_BASE}",
  "stream_id": "${STREAM_ID:-}",
  "stream_key": "${STREAM_KEY:-}"
}
EOF
}
