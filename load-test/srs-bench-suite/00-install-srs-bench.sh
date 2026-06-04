#!/usr/bin/env bash
# Clone and build srs-bench (RTMP + optional WebRTC branches).
set -euo pipefail

RTMP_HOME="${SRS_BENCH_RTMP_HOME:-${HOME}/srs-bench}"
RTC_HOME="${SRS_BENCH_RTC_HOME:-${HOME}/srs-bench-rtc}"

build_rtmp_branch() {
  local dir="$1"
  echo "Building ${dir} (master: ./configure && make)…"
  (cd "${dir}" && ./configure && make)
}

build_rtc_branch() {
  local dir="$1"
  echo "Building ${dir} (feature/rtc: make)…"
  if ! command -v go >/dev/null 2>&1; then
    echo "SKIP: Go not installed (brew install go)" >&2
    return 1
  fi
  local srt_prefix=""
  if command -v brew >/dev/null 2>&1 && brew --prefix srt >/dev/null 2>&1; then
    srt_prefix="$(brew --prefix srt)"
    export CGO_CFLAGS="-I${srt_prefix}/include"
    export CGO_LDFLAGS="-L${srt_prefix}/lib -lsrt"
    echo "Using libsrt from ${srt_prefix}"
  else
    echo "WARN: libsrt not found. Try: brew install srt  (WebRTC bench may fail)" >&2
  fi
  (cd "${dir}" && make)
}

install_branch() {
  local dir="$1" branch="$2"
  local build_fn="$3"
  if [[ -d "${dir}/.git" ]]; then
    echo "Updating ${dir} (${branch})…"
    git -C "${dir}" fetch origin
    git -C "${dir}" checkout "${branch}"
    git -C "${dir}" pull --ff-only origin "${branch}" 2>/dev/null || true
  else
    echo "Cloning srs-bench → ${dir} (branch ${branch})…"
    git clone -b "${branch}" --depth 1 https://github.com/ossrs/srs-bench.git "${dir}"
  fi
  "${build_fn}" "${dir}"
  echo "Done: ${dir}"
  ls -la "${dir}/objs/" 2>/dev/null | head -20 || true
}

echo "=== RTMP tools (required) ==="
install_branch "${RTMP_HOME}" master build_rtmp_branch

echo ""
echo "=== WebRTC bench (optional) ==="
if install_branch "${RTC_HOME}" feature/rtc build_rtc_branch; then
  RTC_OK=1
else
  RTC_OK=0
  echo ""
  echo "WebRTC build failed — you can still run the suite using sb_rtmp_load for Test 2."
  echo "To retry WebRTC later:"
  echo "  brew install go srt"
  echo "  cd ${RTC_HOME} && make"
fi

echo ""
echo "Installed:"
if [[ -x "${RTMP_HOME}/objs/sb_rtmp_publish" ]]; then
  echo "  OK RTMP publish: ${RTMP_HOME}/objs/sb_rtmp_publish"
  echo "  OK RTMP load:    ${RTMP_HOME}/objs/sb_rtmp_load"
else
  echo "  MISSING RTMP tools — check ${RTMP_HOME}/objs" >&2
  exit 1
fi
if [[ "${RTC_OK:-0}" == 1 && -x "${RTC_HOME}/objs/srs_bench" ]]; then
  echo "  OK WebRTC:       ${RTC_HOME}/objs/srs_bench"
else
  echo "  -- WebRTC:       not built (Tests 2/4 use RTMP subscribers instead)"
fi
echo ""
echo "Next: ./load-test/srs-bench-suite/run-all.sh"
