# Cluster suite helpers. Source from test scripts:
#   source "$(dirname "$0")/../lib/common.sh"
set -euo pipefail

CLUSTER_SUITE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT="$(cd "${CLUSTER_SUITE_ROOT}/.." && pwd)"

# shellcheck source=../../../scripts/lib/app-cluster.sh
source "${ROOT}/scripts/lib/app-cluster.sh"

export BASE_URL="${BASE_URL:-${APP_LB_URL}}"
export INTERNAL_API_TOKEN="${INTERNAL_API_TOKEN:-dev-internal-token}"
export CLUSTER_ORIGIN_API="${CLUSTER_ORIGIN_API:-http://127.0.0.1:1995}"
export CLUSTER_WHEP_LB="${CLUSTER_WHEP_LB:-http://127.0.0.1:1985}"
export CLUSTER_PLAYBACK_API="${CLUSTER_PLAYBACK_API:-http://127.0.0.1:1985}"

cluster_report_dir() {
  local label="${1:-run}"
  if [[ -z "${REPORTS_DIR:-}" ]]; then
    REPORTS_DIR="${ROOT}/load-test/reports/cluster-suite-${label}-$(date +%Y%m%d-%H%M%S)"
  fi
  mkdir -p "${REPORTS_DIR}"
  echo "${REPORTS_DIR}"
}

cluster_write_result() {
  local status="$1"
  local message="$2"
  python3 - <<PY
import json, os
from datetime import datetime, timezone
path = os.path.join("${REPORTS_DIR}", "result.json")
data = {
    "status": "${status}",
    "message": """${message}""",
    "finished_at": datetime.now(timezone.utc).isoformat(),
}
with open(path, "w") as f:
    json.dump(data, f, indent=2)
PY
}

cluster_pass() {
  echo "PASS ${TEST_ID:-test}: $*"
  cluster_write_result "PASS" "$*"
}

cluster_fail() {
  echo "FAIL ${TEST_ID:-test}: $*" >&2
  cluster_write_result "FAIL" "$*"
  exit 1
}

cluster_curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local tmp
  tmp="$(mktemp)"
  local code
  if [[ -n "${body}" ]]; then
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' --max-time 30 -X "${method}" "${url}" \
      -H 'Content-Type: application/json' -d "${body}")"
  else
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' --max-time 30 -X "${method}" "${url}")"
  fi
  if [[ "${code}" != "200" && "${code}" != "201" && "${code}" != "204" ]]; then
    echo "HTTP ${code} ${method} ${url}" >&2
    [[ -s "${tmp}" ]] && cat "${tmp}" >&2
    rm -f "${tmp}"
    return 1
  fi
  cat "${tmp}"
  rm -f "${tmp}"
}

# Prefer host redis-cli (port 6379) — docker exec can hang and stall tests.
cluster_redis() {
  if command -v redis-cli >/dev/null 2>&1; then
    redis-cli -h 127.0.0.1 -p 6379 "$@"
  else
    app_cluster_rcli "$@"
  fi
}

cluster_cleanup_streams() {
  echo "cleanup: stopping any LIVE streams via ${BASE_URL}..."
  local json
  json="$(curl -sf --max-time 10 "${BASE_URL}/streams" 2>/dev/null || echo "[]")"
  if [[ -z "${json}" ]]; then
    json="[]"
  fi
  printf '%s' "${json}" | python3 -c "
import json, os, subprocess, sys
base = os.environ.get('BASE_URL', 'http://127.0.0.1:8080')
raw = sys.stdin.read().strip() or '[]'
try:
    streams = json.loads(raw)
except json.JSONDecodeError:
    streams = []
for s in streams:
    sid = s.get('id')
    if sid is None:
        continue
    r = subprocess.run(
        ['curl', '-sS', '-o', '/dev/null', '-w', '%{http_code}',
         '--max-time', '20', '-X', 'POST', f'{base}/streams/{sid}/stop'],
        capture_output=True, text=True)
    print(f'  stopped stream {sid} (HTTP {r.stdout.strip()})')
" || true
  sleep 1
}

cluster_start_stream() {
  local base_url="${1:-${BASE_URL}}"
  local title="${2:-cluster-suite}"
  local tmp
  tmp="$(mktemp)"
  if ! cluster_curl_json POST "${base_url}/streams/start" \
      "{\"broadcasterId\":1,\"title\":\"${title}\",\"delivery\":\"webrtc\"}" >"${tmp}"; then
    rm -f "${tmp}"
    cluster_fail "POST /streams/start failed (broadcaster 1 may already have a LIVE/PAUSED stream — run cluster_cleanup_streams)"
  fi
  python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['id'])" "${tmp}"
  rm -f "${tmp}"
}

cluster_stop_stream() {
  local stream_id="$1"
  local base_url="${2:-${BASE_URL}}"
  cluster_curl_json POST "${base_url}/streams/${stream_id}/stop" >/dev/null
}

# Wait until stream appears on GET /streams (ingestLivenessFilter needs FFmpeg/RTMP active).
cluster_wait_stream_visible() {
  local stream_id="$1"
  local base_url="${2:-${BASE_URL}}"
  local max_attempts="${3:-30}"
  local i=0
  echo "waiting for stream ${stream_id} on ${base_url}/streams (ingest liveness)…"
  while [[ "${i}" -lt "${max_attempts}" ]]; do
    if curl -sf --max-time 5 "${base_url}/streams" | python3 -c "
import json, sys
sid = int(sys.argv[1])
streams = json.load(sys.stdin)
sys.exit(0 if any(s.get('id') == sid for s in streams) else 1)
" "${stream_id}" 2>/dev/null; then
      echo "  OK   stream ${stream_id} visible to viewers"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  cluster_fail "stream ${stream_id} not listed on ${base_url}/streams after $((max_attempts * 2))s (FFmpeg ingest?)"
}

# PID of the most recent cluster_keepalive_start (do not capture via $(...) — subshell waits on bg jobs).
CLUSTER_LAST_KEEPALIVE_PID=""

cluster_keepalive_start() {
  local stream_id="$1"
  local base_url="${2:-${BASE_URL}}"
  (
    while true; do
      curl -sS --max-time 10 -X POST "${base_url}/streams/${stream_id}/broadcaster-heartbeat" >/dev/null 2>&1 || true
      sleep 4
    done
  ) &
  CLUSTER_LAST_KEEPALIVE_PID=$!
  # Required when callers wrongly use KEEPALIVE_PID="$(cluster_keepalive_start ...)" — the $( )
  # subshell otherwise blocks until this infinite loop exits.
  disown "${CLUSTER_LAST_KEEPALIVE_PID}" 2>/dev/null || true
}

cluster_keepalive_stop() {
  local pid="${1:-}"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    # Background loop may be in sleep 4 — don't block the suite on wait.
    ( wait "${pid}" 2>/dev/null ) &
    local wp=$!
    local i=0
    while kill -0 "${wp}" 2>/dev/null && [[ "${i}" -lt 6 ]]; do
      sleep 1
      i=$((i + 1))
    done
    kill "${wp}" 2>/dev/null || true
  fi
}
