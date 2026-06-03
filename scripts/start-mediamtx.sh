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
  echo "MediaMTX is already running. Stop or restart:"
  echo "  ./scripts/stop-mediamtx.sh"
  echo "  ./scripts/restart-mediamtx.sh"
  pgrep -fl mediamtx
  exit 1
fi
for port in 1935 8889 9997; do
  if lsof -tiTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${port} is in use by another process. Run ./scripts/stop-mediamtx.sh first."
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN
    exit 1
  fi
done
echo "Starting MediaMTX (RTMP :1935, WebRTC :8889, API :9997)…"
echo "Stop with Ctrl+C in this terminal, or: ./scripts/stop-mediamtx.sh"
exec mediamtx mediamtx.yml
