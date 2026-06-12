# Shared helpers for load-test scripts. Source from other scripts:
#   source "$(dirname "$0")/../lib/common.sh"

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
RTMP_BASE="${RTMP_BASE:-rtmp://127.0.0.1:1935}"
MEDIA_BACKEND="${MEDIA_BACKEND:-srs}"
if [[ "${MEDIA_BACKEND}" == "srs" ]]; then
  WHEP_DIRECT_BASE="${WHEP_DIRECT_BASE:-http://127.0.0.1:1985/rtc/v1/whep}"
  SRS_API_BASE="${SRS_API_BASE:-http://127.0.0.1:1985}"
else
  WHEP_DIRECT_BASE="${WHEP_DIRECT_BASE:-http://127.0.0.1:8889}"
fi
WHEP_PROXY_BASE="${WHEP_PROXY_BASE:-${BASE_URL}/whep}"

SRS_BENCH_RTMP_HOME="${SRS_BENCH_RTMP_HOME:-${HOME}/srs-bench}"
SRS_BENCH_RTC_HOME="${SRS_BENCH_RTC_HOME:-${HOME}/srs-bench-rtc}"

# macOS has no `timeout` unless coreutils is installed (gtimeout).
run_with_timeout() {
  local secs="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "${secs}" "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    gtimeout "${secs}" "$@"
  else
    perl -e 'alarm shift; exec @ARGV' "${secs}" "$@"
  fi
}

load_live_stream() {
  local json
  json="$(curl -sf "${BASE_URL}/streams")" || {
    echo "ERROR: GET ${BASE_URL}/streams failed. Start the app: ./mvnw server config/config-dev.yml" >&2
    return 1
  }
  if [[ -z "${json}" ]]; then
    echo "ERROR: empty response from ${BASE_URL}/streams" >&2
    return 1
  fi
  local parsed
  parsed="$(printf '%s' "${json}" | python3 -c "
import json, sys, shlex
try:
    live = json.load(sys.stdin)
except json.JSONDecodeError as e:
    print('ERROR: invalid JSON from /streams:', e, file=sys.stderr)
    sys.exit(1)
if not live:
    print('ERROR: no LIVE stream. Start one on http://127.0.0.1:8080/ui/broadcast.html', file=sys.stderr)
    sys.exit(1)
s = live[0]
kid = s['id']
key = s.get('streamKey') or ''
title = s.get('title') or ''
print('STREAM_ID=' + str(kid))
print('STREAM_KEY=' + shlex.quote(key))
print('STREAM_TITLE=' + shlex.quote(title))
")" || {
    echo "ERROR: could not parse live stream from API" >&2
    return 1
  }
  eval "${parsed}"
  export STREAM_ID STREAM_KEY STREAM_TITLE
}

report_dir() {
  local label="${1:-run}"
  if [[ -z "${REPORTS_DIR:-}" ]]; then
    local prefix="srs-suite"
    [[ "${MEDIA_BACKEND}" == "mediamtx" ]] && prefix="mediamtx-suite"
    REPORTS_DIR="load-test/reports/${prefix}-${label}-$(date +%Y%m%d-%H%M%S)"
  fi
  mkdir -p "${REPORTS_DIR}"
  echo "${REPORTS_DIR}"
}

save_postmortem() {
  local label="${1:-snapshot}"
  local out="${REPORTS_DIR}/postmortem-${label}.json"
  if curl -sf "${BASE_URL}/streams/${STREAM_ID}/postmortem" -o "${out}"; then
    echo "Saved postmortem → ${out}"
  else
    echo "WARN: postmortem save failed (stream ${STREAM_ID})" >&2
  fi
}

save_qos_snapshot() {
  local label="${1:-qos}"
  local out="${REPORTS_DIR}/qos-${label}.json"
  if curl -sf "${BASE_URL}/streams/${STREAM_ID}/qos" -o "${out}"; then
    echo "Saved QoS snapshot → ${out}"
  fi
}

sb_rtmp_publish_bin() {
  if [[ -x "${SRS_BENCH_RTMP_HOME}/objs/sb_rtmp_publish" ]]; then
    echo "${SRS_BENCH_RTMP_HOME}/objs/sb_rtmp_publish"
    return 0
  fi
  return 1
}

# FLV looped by sb_rtmp_publish (-i). All bench publishers may share one file.
bench_publish_flv() {
  local repo_root="${1:?repo root required}"
  local bundled="${SRS_BENCH_RTMP_HOME}/doc/source.200kbps.768x320.flv"
  if [[ -f "${bundled}" ]]; then
    echo "${bundled}"
    return 0
  fi
  local out="${repo_root}/load-test/fixtures/bench-testsrc.flv"
  if [[ -f "${out}" ]]; then
    echo "${out}"
    return 0
  fi
  local ffmpeg="${FFMPEG:-ffmpeg}"
  if ! command -v "${ffmpeg}" >/dev/null 2>&1; then
    echo "ERROR: no bench FLV. Install srs-bench (has doc/source*.flv) or ffmpeg to generate ${out}" >&2
    return 1
  fi
  mkdir -p "$(dirname "${out}")"
  echo "Generating 10s color-bar FLV → ${out}" >&2
  "${ffmpeg}" -y -loglevel error \
    -f lavfi -i testsrc=size=640x360:rate=15 \
    -f lavfi -i sine=frequency=440:sample_rate=44100 \
    -t 10 -map 0:v:0 -map 1:a:0 \
    -c:v libx264 -preset ultrafast -profile:v baseline -pix_fmt yuv420p \
    -g 15 -keyint_min 15 -sc_threshold 0 -bf 0 \
    -c:a aac -b:a 64k -ar 44100 \
    -f flv -flvflags no_duration_filesize \
    "${out}"
  echo "${out}"
}

sb_rtmp_load_bin() {
  if [[ -x "${SRS_BENCH_RTMP_HOME}/objs/sb_rtmp_load" ]]; then
    echo "${SRS_BENCH_RTMP_HOME}/objs/sb_rtmp_load"
    return 0
  fi
  return 1
}

sb_rtc_bench_bin() {
  if [[ -x "${SRS_BENCH_RTC_HOME}/objs/srs_bench" ]]; then
    echo "${SRS_BENCH_RTC_HOME}/objs/srs_bench"
    return 0
  fi
  return 1
}

require_app() {
  curl -sf "${BASE_URL}/streams" >/dev/null || {
    echo "ERROR: App not reachable at ${BASE_URL}" >&2
    exit 1
  }
}

require_live_stream() {
  require_app
  load_live_stream || exit 1
  echo "Live stream: id=${STREAM_ID} key=${STREAM_KEY} title=${STREAM_TITLE}"
}

whep_url_direct() {
  local stream="${1:-${STREAM_KEY}_high}"
  if [[ "${MEDIA_BACKEND}" == "srs" ]]; then
    echo "${WHEP_DIRECT_BASE}/?app=live&stream=${stream}"
  else
    echo "${WHEP_DIRECT_BASE}/live/${stream}/whep"
  fi
}

whep_url_proxy() {
  local stream="${1:-${STREAM_KEY}_high}"
  echo "${WHEP_PROXY_BASE}/live/${stream}/whep"
}

rtmp_publish_url() {
  local suffix="${1:-_high}"
  local path="${2:-${STREAM_KEY}}"
  echo "${RTMP_BASE}/live/${path}${suffix}"
}
