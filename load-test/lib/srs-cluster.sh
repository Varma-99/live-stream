# SRS origin + edge cluster helpers. Source after common.sh + srs.sh:
#   source "${ROOT}/load-test/lib/common.sh"
#   source "${ROOT}/load-test/lib/srs.sh"
#   source "${ROOT}/load-test/lib/srs-cluster.sh"

CLUSTER_ORIGIN_API="${CLUSTER_ORIGIN_API:-http://127.0.0.1:1995}"
CLUSTER_WHEP_LB="${CLUSTER_WHEP_LB:-http://127.0.0.1:1985}"
CLUSTER_EDGE1_NAME="${CLUSTER_EDGE1_NAME:-srs-edge-1}"
CLUSTER_EDGE2_NAME="${CLUSTER_EDGE2_NAME:-srs-edge-2}"
CLUSTER_LB_NAME="${CLUSTER_LB_NAME:-srs-lb}"
CLUSTER_ORIGIN_NAME="${CLUSTER_ORIGIN_NAME:-srs-origin}"
CLUSTER_MIN_SDP="${CLUSTER_MIN_SDP:-${ROOT}/load-test/fixtures/min-whep-offer.sdp}"

# Reliable on macOS (avoid brittle `docker ps | grep -qx`).
docker_container_status() {
  local name="${1:?container name}"
  docker inspect -f '{{.State.Status}}' "${name}" 2>/dev/null || echo "missing"
}

docker_container_running() {
  [[ "$(docker_container_status "$1")" == "running" ]]
}

cluster_print_docker_state() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "  (docker CLI not found)" >&2
    return 0
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "  (Docker daemon not reachable — is Colima running?)" >&2
    return 0
  fi
  echo "  Docker srs* containers:" >&2
  local line
  while IFS= read -r line; do
    [[ -n "${line}" ]] && echo "    ${line}" >&2
  done < <(docker ps -a --filter "name=srs" --format '{{.Names}}\t{{.Status}}' 2>/dev/null || true)
}

cluster_http_ok() {
  curl -sf --max-time 5 "${CLUSTER_ORIGIN_API}/api/v1/streams/" >/dev/null \
    && curl -sf --max-time 5 "${CLUSTER_WHEP_LB}/api/v1/streams/" >/dev/null
}

apply_cluster_env() {
  export SRS_API_BASE="${CLUSTER_ORIGIN_API}"
  export SRS_WHEP_BASE="${CLUSTER_WHEP_LB}"
  export WHEP_DIRECT_BASE="${CLUSTER_WHEP_LB}/rtc/v1/whep"
  export MEDIA_BACKEND=srs
}

report_dir_cluster() {
  local label="${1:-run}"
  if [[ -z "${REPORTS_DIR:-}" ]]; then
    REPORTS_DIR="${ROOT}/load-test/reports/srs-cluster-suite-${label}-$(date +%Y%m%d-%H%M%S)"
  fi
  mkdir -p "${REPORTS_DIR}"
  echo "${REPORTS_DIR}"
}

suite_meta_cluster() {
  cat > "${REPORTS_DIR}/suite-meta.json" <<EOF
{
  "backend": "srs-cluster",
  "cluster_mode": "edge",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "base_url": "${BASE_URL}",
  "origin_api": "${CLUSTER_ORIGIN_API}",
  "whep_lb": "${CLUSTER_WHEP_LB}",
  "stream_id": "${STREAM_ID:-}",
  "stream_key": "${STREAM_KEY:-}"
}
EOF
}

require_cluster_stack() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: docker CLI not found." >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker daemon not reachable (Colima running?)." >&2
    echo "  Try: colima start" >&2
    exit 1
  fi

  # HTTP is the real gate — if origin + LB answer, tests can run.
  if cluster_http_ok; then
    return 0
  fi

  echo "ERROR: SRS edge cluster is not healthy." >&2

  if ! curl -sf --max-time 5 "${CLUSTER_ORIGIN_API}/api/v1/streams/" >/dev/null; then
    echo "  Origin API failed: ${CLUSTER_ORIGIN_API}/api/v1/streams/" >&2
    echo "    container srs-origin: $(docker_container_status srs-origin)" >&2
  fi

  if ! curl -sf --max-time 5 "${CLUSTER_WHEP_LB}/api/v1/streams/" >/dev/null; then
    echo "  WHEP LB failed: ${CLUSTER_WHEP_LB}/api/v1/streams/" >&2
    echo "    container srs-lb: $(docker_container_status srs-lb)" >&2
    if [[ "$(docker_container_status srs-lb)" == "restarting" ]]; then
      echo "  Hint: HAProxy config error — docker logs srs-lb --tail 20" >&2
    fi
  fi

  cluster_print_docker_state
  echo "  Fix: ./scripts/stop-srs.sh && SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh" >&2
  exit 1
}

edge_streams_json() {
  local edge="${1:?edge container name required}"
  docker exec "${edge}" curl -sf --max-time 8 "http://127.0.0.1:1985/api/v1/streams/"
}

save_edge_streams() {
  local edge="${1:?edge}"
  local label="${2:-snapshot}"
  local safe
  safe="$(echo "${edge}" | tr -c '[:alnum:]_-' '_')"
  local out="${REPORTS_DIR}/edge-${safe}-${label}.json"
  if edge_streams_json "${edge}" > "${out}"; then
    echo "Saved ${edge} streams → ${out}"
  else
    echo "WARN: could not save streams from ${edge}" >&2
  fi
}

find_serving_edge() {
  local base_key="${1:?base stream key required}"
  local e1 e2
  e1="$(edge_streams_json "${CLUSTER_EDGE1_NAME}" 2>/dev/null || echo '{"streams":[]}')"
  e2="$(edge_streams_json "${CLUSTER_EDGE2_NAME}" 2>/dev/null || echo '{"streams":[]}')"
  CLUSTER_FIND_BASE="${base_key}" CLUSTER_EDGE1_JSON="${e1}" CLUSTER_EDGE2_JSON="${e2}" python3 - <<'PY'
import json, os

base = os.environ["CLUSTER_FIND_BASE"]
targets = {f"{base}_high", f"{base}_mid", f"{base}_low", base}

def has_targets(blob):
    try:
        data = json.loads(blob)
    except json.JSONDecodeError:
        return False
    names = {s.get("name") for s in data.get("streams", [])}
    return bool(names & targets)

hits = []
if has_targets(os.environ["CLUSTER_EDGE1_JSON"]):
    hits.append("srs-edge-1")
if has_targets(os.environ["CLUSTER_EDGE2_JSON"]):
    hits.append("srs-edge-2")
print(",".join(hits) if hits else "none")
PY
}

whep_handshake_once() {
  local stream="${1:?stream path required}"
  local out_sdp="${2:-/dev/null}"
  local url="${CLUSTER_WHEP_LB}/rtc/v1/whep/?app=live&stream=${stream}"
  if [[ ! -f "${CLUSTER_MIN_SDP}" ]]; then
    echo "ERROR: missing ${CLUSTER_MIN_SDP}" >&2
    return 1
  fi
  curl -sS -o "${out_sdp}" -w '%{http_code} %{time_total}' \
    -X POST "${url}" \
    -H 'Content-Type: application/sdp' \
    --data-binary "@${CLUSTER_MIN_SDP}" \
    --max-time 20 2>/dev/null || echo "000 0"
}

origin_pull_stats() {
  curl -sf --max-time 8 "${CLUSTER_ORIGIN_API}/api/v1/streams/" | python3 - <<'PY'
import json, sys
data = json.load(sys.stdin)
paths = 0
pull_clients = 0
publishers = 0
for s in data.get("streams", []):
    if s.get("publish", {}).get("active"):
        publishers += 1
        paths += 1
        pull_clients += int(s.get("clients") or 0)
print(json.dumps({
    "published_paths": paths,
    "edge_pull_clients": pull_clients,
    "stream_count": len(data.get("streams", [])),
}))
PY
}

save_origin_pull_stats() {
  local label="${1:-snapshot}"
  local out="${REPORTS_DIR}/origin-pulls-${label}.json"
  if origin_pull_stats > "${out}"; then
    echo "Saved origin pull stats → ${out}"
  else
    echo "WARN: origin pull stats failed" >&2
  fi
}

run_k6_whep() {
  local summary="${1:?summary json path}"
  local vus="${2:-30}"
  local ramp="${3:-15}"
  local hold="${4:-45}"
  local ramp_down="${5:-10}"
  k6 run \
    --env BASE_URL="${BASE_URL}" \
    --env STREAM_KEY="${STREAM_KEY}" \
    --env TARGET_VUS="${vus}" \
    --env RAMP_UP_SEC="${ramp}" \
    --env HOLD_SEC="${hold}" \
    --env RAMP_DOWN_SEC="${ramp_down}" \
    --summary-export="${summary}" \
    "${ROOT}/load-test/k6-whep-handshake.js" || return 1
}

k6_handshake_ok_rate() {
  local summary="${1:?k6 summary json}"
  python3 - <<'PY' "${summary}"
import json, sys
path = sys.argv[1]
try:
    data = json.load(open(path))
except (OSError, json.JSONDecodeError):
    print("0")
    sys.exit(0)
metric = data.get("metrics", {}).get("whep_handshake_ok", {})
rate = metric.get("values", {}).get("rate")
print(rate if rate is not None else "0")
PY
}

wait_for_origin_path() {
  local path_name="${1:?stream path name}"
  local timeout_sec="${2:-60}"
  local start
  start="$(date +%s)"
  while true; do
    if origin_path_active "${path_name}"; then
      return 0
    fi
    if (( $(date +%s) - start >= timeout_sec )); then
      echo "ERROR: timed out waiting for origin publish ${path_name}" >&2
      return 1
    fi
    sleep 1
  done
}

origin_path_active() {
  local path_name="${1:?stream path name}"
  curl -sf --max-time 5 "${CLUSTER_ORIGIN_API}/api/v1/streams/" | python3 -c "
import json, sys
name = sys.argv[1]
for s in json.load(sys.stdin):
    if s.get('name') == name and s.get('publish', {}).get('active'):
        sys.exit(0)
sys.exit(1)
" "${path_name}" 2>/dev/null
}

wait_for_serving_edge() {
  local base_key="${1:?base key}"
  local timeout_sec="${2:-60}"
  local start
  start="$(date +%s)"
  while true; do
    local edge
    edge="$(find_serving_edge "${base_key}")"
    if [[ "${edge}" != "none" && -n "${edge}" ]]; then
      echo "${edge}"
      return 0
    fi
    if (( $(date +%s) - start >= timeout_sec )); then
      echo "none"
      return 1
    fi
    sleep 1
  done
}

ensure_live_stream_or_dummy() {
  if load_live_stream 2>/dev/null; then
    echo "Using live stream id=${STREAM_ID} key=${STREAM_KEY}"
    return 0
  fi
  echo "No LIVE stream — starting cluster test dummy…"
  local title="cluster-suite-$(date +%H%M%S)"
  local resp
  resp="$(curl -sf -X POST "${BASE_URL}/streams/start-dummy" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"${title}\",\"delivery\":\"webrtc\"}")" || {
    echo "ERROR: could not start dummy stream at ${BASE_URL}/streams/start-dummy" >&2
    exit 1
  }
  sleep 3
  load_live_stream || {
    echo "ERROR: dummy stream did not appear in GET /streams" >&2
    exit 1
  }
  echo "Started dummy stream id=${STREAM_ID} key=${STREAM_KEY}"
}

stream_is_dummy() {
  curl -sf "${BASE_URL}/streams" | python3 -c "
import json, sys
sid = sys.argv[1]
for s in json.load(sys.stdin):
    if str(s.get('id')) == sid:
        print('true' if s.get('dummy') else 'false')
        sys.exit(0)
print('false')
" "${STREAM_ID}"
}

wait_for_origin_abr() {
  local base_key="${1:?base key}"
  local timeout_sec="${2:-90}"
  for suffix in _high _mid _low; do
    wait_for_origin_path "${base_key}${suffix}" "${timeout_sec}" || return 1
  done
}

origin_has_stream() {
  local base_key="${1:?base key}"
  curl -sf --max-time 5 "${CLUSTER_ORIGIN_API}/api/v1/streams/" | python3 -c "
import json, sys
base = sys.argv[1]
targets = {f'{base}_high', f'{base}_mid', f'{base}_low'}
names = {s.get('name') for s in json.load(sys.stdin).get('streams', [])}
sys.exit(0 if targets & names else 1)
" "${base_key}" 2>/dev/null
}

warm_stream_whep() {
  local base_key="${1:-${STREAM_KEY}}"
  local attempts="${2:-8}"
  echo "Waiting for ABR publish on origin…"
  wait_for_origin_abr "${base_key}" 120 || echo "WARN: not all ABR paths active on origin yet"
  local i result code
  for ((i = 1; i <= attempts; i++)); do
    result="$(whep_handshake_once "${base_key}_high" "${REPORTS_DIR}/warm-${i}.sdp")"
    code="${result%% *}"
    if [[ "${code}" == "200" || "${code}" == "201" ]]; then
      echo "WHEP warm-up ok (attempt ${i})"
      return 0
    fi
    echo "WHEP warm-up attempt ${i}: ${result}"
    sleep 3
  done
  echo "ERROR: WHEP warm-up failed after ${attempts} attempts (last=${result})" >&2
  echo "  Check: docker logs srs-origin --tail 30" >&2
  return 1
}

warm_stream_on_edge() {
  warm_stream_whep "$@"
}

restart_origin_container() {
  docker start "${CLUSTER_ORIGIN_NAME}" >/dev/null
  local attempt
  for attempt in $(seq 1 30); do
    if curl -sf --max-time 3 "${CLUSTER_ORIGIN_API}/api/v1/streams/" >/dev/null; then
      echo "  ${CLUSTER_ORIGIN_NAME} is healthy"
      return 0
    fi
    sleep 2
  done
  echo "WARN: ${CLUSTER_ORIGIN_NAME} did not become healthy in time" >&2
  return 1
}

restart_edge_container() {
  local edge="${1:?edge container}"
  docker start "${edge}" >/dev/null
  local attempt
  for attempt in $(seq 1 20); do
    if docker_container_running "${edge}"; then
      echo "  ${edge} is running"
      return 0
    fi
    sleep 2
  done
  echo "WARN: ${edge} did not become healthy in time" >&2
  return 1
}
