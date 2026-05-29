#!/usr/bin/env bash
# Start MediaMTX for WebRTC playback (install: brew install mediamtx)
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v mediamtx >/dev/null 2>&1; then
  echo "MediaMTX not found. Install: brew install mediamtx"
  echo "Or download from https://github.com/bluenviron/mediamtx/releases"
  exit 1
fi
if pgrep -x mediamtx >/dev/null 2>&1; then
  echo "MediaMTX is already running. Stop it first:"
  echo "  kill \$(pgrep -x mediamtx)"
  echo "Or: pkill mediamtx"
  exit 1
fi
echo "Starting MediaMTX (RTMP :1935, WebRTC :8889, LAN WebRTC host 192.168.29.140)…"
exec mediamtx mediamtx.yml
