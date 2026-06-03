#!/usr/bin/env bash
# Stop all MediaMTX processes and free RTMP/WebRTC/API ports.
set -euo pipefail
cd "$(dirname "$0")/.."

PORTS=(1935 8889 9997)
PIDS=$(pgrep -x mediamtx 2>/dev/null || true)

if [[ -z "${PIDS}" ]]; then
  echo "No mediamtx process found (pgrep -x mediamtx)."
else
  echo "Stopping MediaMTX PIDs: ${PIDS}"
  kill ${PIDS} 2>/dev/null || true
  sleep 1
  STILL=$(pgrep -x mediamtx 2>/dev/null || true)
  if [[ -n "${STILL}" ]]; then
    echo "Force killing: ${STILL}"
    kill -9 ${STILL} 2>/dev/null || true
    sleep 1
  fi
fi

# Also stop stray listeners on our ports (orphan after bad terminal close)
for port in "${PORTS[@]}"; do
  listeners=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)
  if [[ -n "${listeners}" ]]; then
    echo "Freeing port ${port} (PID(s): ${listeners})"
    kill ${listeners} 2>/dev/null || true
    sleep 0.5
    listeners=$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)
    if [[ -n "${listeners}" ]]; then
      kill -9 ${listeners} 2>/dev/null || true
    fi
  fi
done

if pgrep -x mediamtx >/dev/null 2>&1; then
  echo "Warning: mediamtx still running."
  pgrep -fl mediamtx
  exit 1
fi

for port in "${PORTS[@]}"; do
  if lsof -tiTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Warning: port ${port} still in use."
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN
    exit 1
  fi
done

echo "MediaMTX stopped. Ports ${PORTS[*]} are free."
