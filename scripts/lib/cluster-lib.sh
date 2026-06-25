#!/usr/bin/env bash
# Shared orchestration helpers for scripts/start-cluster.sh and stop-cluster.sh.
set -euo pipefail

CLUSTER_SCRIPTS_LIB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_SCRIPTS_ROOT="$(cd "${CLUSTER_SCRIPTS_LIB}/.." && pwd)"
CLUSTER_REPO_ROOT="$(cd "${CLUSTER_SCRIPTS_ROOT}/.." && pwd)"

# shellcheck source=app-cluster.sh
source "${CLUSTER_SCRIPTS_LIB}/app-cluster.sh"

cluster_ensure_dirs() {
  mkdir -p "${CLUSTER_PID_DIR}" "${CLUSTER_LOG_DIR}"
}

cluster_step() {
  echo ""
  echo "━━ $(printf '%s' "$*" | tr '[:lower:]' '[:upper:]') ━━"
}

cluster_info() {
  echo "  $*"
}

cluster_fail() {
  echo "FAIL $*" >&2
  exit 1
}

# wait_for_health "label" "url" [max_attempts]  — each attempt sleeps 2s (default 15 → ~30s).
wait_for_health() {
  local label="$1"
  local url="$2"
  local max="${3:-15}"
  local i=0
  while ! curl -sf --max-time 2 "${url}" >/dev/null 2>&1; do
    i=$((i + 1))
    if [[ "${i}" -ge "${max}" ]]; then
      echo "  FAIL ${label} (timeout waiting for ${url})" >&2
      return 1
    fi
    sleep 2
  done
  echo "  OK   ${label}"
}

# wait_for_cmd "label" "command" [max_attempts]
wait_for_cmd() {
  local label="$1"
  local cmd="$2"
  local max="${3:-15}"
  local i=0
  while ! eval "${cmd}" >/dev/null 2>&1; do
    i=$((i + 1))
    if [[ "${i}" -ge "${max}" ]]; then
      echo "  FAIL ${label} (timeout: ${cmd})" >&2
      return 1
    fi
    sleep 2
  done
  echo "  OK   ${label}"
}

cluster_require_colima() {
  if ! command -v colima >/dev/null 2>&1; then
    cluster_info "colima not installed — assuming Docker Desktop or other runtime"
    app_cluster_require_docker
    return 0
  fi
  if ! colima status 2>&1 | grep -qi 'colima is running'; then
    cluster_fail "Colima is not running. Start with: colima start --port-forwarder=grpc"
  fi
  local pf="unknown"
  local colima_yaml="${HOME}/.colima/default/colima.yaml"
  if [[ -f "${colima_yaml}" ]]; then
    pf="$(awk '/^portForwarder:/ {print $2}' "${colima_yaml}" 2>/dev/null || true)"
  fi
  if [[ "${pf}" != "grpc" ]]; then
    echo "WARN: Colima portForwarder=${pf:-unknown} (WebRTC needs grpc)." >&2
    echo "      Fix: colima stop && colima start --port-forwarder=grpc" >&2
  else
    cluster_info "Colima running (portForwarder=grpc)"
  fi
  app_cluster_require_docker
}

cluster_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    "${COMPOSE}" "$@"
  fi
}

cluster_port_in_use() {
  local port="$1"
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
}

cluster_require_port_free() {
  local port="$1"
  local label="$2"
  if cluster_port_in_use "${port}"; then
    echo "Port ${port} (${label}) is already in use:" >&2
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
    return 1
  fi
  return 0
}

cluster_find_jar() {
  local jar
  jar="$(ls -1 "${CLUSTER_REPO_ROOT}"/target/live-stream-*.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    return 1
  fi
  echo "${jar}"
}

cluster_write_pid() {
  local name="$1"
  local pid="$2"
  echo "${pid}" >"${CLUSTER_PID_DIR}/${name}.pid"
}

cluster_read_pid() {
  local name="$1"
  local file="${CLUSTER_PID_DIR}/${name}.pid"
  if [[ -f "${file}" ]]; then
    cat "${file}"
  fi
}

cluster_stop_pid() {
  local name="$1"
  local pidfile="${CLUSTER_PID_DIR}/${name}.pid"
  if [[ ! -f "${pidfile}" ]]; then
    return 0
  fi
  local pid
  pid="$(cat "${pidfile}")"
  if kill -0 "${pid}" 2>/dev/null; then
    echo "  stopping ${name} (pid ${pid})…"
    kill -TERM "${pid}" 2>/dev/null || true
    local i=0
    while kill -0 "${pid}" 2>/dev/null && [[ "${i}" -lt 10 ]]; do
      sleep 1
      i=$((i + 1))
    done
    if kill -0 "${pid}" 2>/dev/null; then
      echo "  force-kill ${name} (pid ${pid})" >&2
      kill -KILL "${pid}" 2>/dev/null || true
    fi
  fi
  rm -f "${pidfile}"
}

cluster_kill_port() {
  local port="$1"
  local pid
  while read -r pid; do
    [[ -z "${pid}" ]] && continue
    cluster_info "stopping pid ${pid} listening on :${port}"
    kill -TERM "${pid}" 2>/dev/null || true
  done < <(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)
  sleep 2
  while read -r pid; do
    [[ -z "${pid}" ]] && continue
    kill -KILL "${pid}" 2>/dev/null || true
  done < <(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)
}

cluster_cleanup_live_streams() {
  cluster_info "stopping any LIVE streams via ${APP_LB_URL}…"
  local json
  json="$(curl -sf --max-time 10 "${APP_LB_URL}/streams" 2>/dev/null || echo "[]")"
  [[ -n "${json}" ]] || json="[]"
  printf '%s' "${json}" | python3 -c "
import json, os, subprocess, sys
base = os.environ.get('APP_LB_URL', 'http://127.0.0.1:8080')
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

cluster_start_java_app() {
  local name="$1"
  local config="$2"
  local port="$3"
  local jar="$4"
  local log="${CLUSTER_LOG_DIR}/${name}.log"

  cluster_stop_pid "${name}"
  if cluster_port_in_use "${port}"; then
    cluster_info "${name}: recycling listener on :${port}"
    cluster_kill_port "${port}"
  fi

  cluster_info "starting ${name} → config/${config} (log: ${log})"
  (
    cd "${CLUSTER_REPO_ROOT}"
    exec java -jar "${jar}" server "config/${config}"
  ) >>"${log}" 2>&1 &
  cluster_write_pid "${name}" "$!"

  wait_for_health "${name}" "http://127.0.0.1:${port}/internal/health" "${5:-60}"
}

cluster_smoke_stream() {
  local base="${APP_LB_URL}"
  export APP_LB_URL="${base}"
  cluster_cleanup_live_streams
  cluster_info "smoke: POST ${base}/streams/start"
  local tmp code
  tmp="$(mktemp)"
  code="$(curl -sS -o "${tmp}" -w '%{http_code}' --max-time 30 -X POST "${base}/streams/start" \
    -H 'Content-Type: application/json' \
    -d '{"broadcasterId":1,"title":"cluster-smoke","delivery":"webrtc"}')"
  if [[ "${code}" != "200" ]]; then
    echo "  FAIL smoke stream (HTTP ${code})" >&2
    [[ -s "${tmp}" ]] && cat "${tmp}" >&2
    rm -f "${tmp}"
    return 1
  fi
  local sid
  sid="$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['id'])" "${tmp}")"
  rm -f "${tmp}"
  echo "  OK   smoke stream id=${sid}"
  curl -sS -o /dev/null --max-time 20 -X POST "${base}/streams/${sid}/stop" || true
}

cluster_print_summary() {
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  Cluster is up                                                ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  echo "Endpoints:"
  app_cluster_print_endpoints
  echo "  SRS origin API:      http://127.0.0.1:1995/api/v1/streams/"
  echo "  SRS WHEP LB:         http://127.0.0.1:1985/api/v1/streams/"
  echo ""
  echo "Logs:    ${CLUSTER_LOG_DIR}/"
  echo "PIDs:    ${CLUSTER_PID_DIR}/"
  echo ""
  echo "Tests:   PROFILE=full ./load-test/cluster-suite/run-all.sh"
  echo "Stop:    ./scripts/stop-cluster.sh"
  echo "         (Ctrl+C in this terminal also stops the cluster)"
}
