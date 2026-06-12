#!/usr/bin/env bash
# Stop MediaMTX processes only. Does NOT kill Colima/Docker port forwards on 1935.
set -euo pipefail
cd "$(dirname "$0")/.."

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

if pgrep -x mediamtx >/dev/null 2>&1; then
  echo "Warning: mediamtx still running."
  pgrep -fl mediamtx
  exit 1
fi

# Warn if ports still busy (e.g. SRS Docker on 1935 — use stop-srs.sh, not this script)
for port in 1935 8889 9997; do
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Note: port ${port} still in use (may be SRS/Colima, not MediaMTX):"
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN || true
    if [[ "${port}" == "1935" ]]; then
      echo "If SRS is running: ./scripts/stop-srs.sh"
      echo "Do not kill ssh/Colima processes — that breaks Docker."
    fi
  fi
done

echo "MediaMTX stopped."
