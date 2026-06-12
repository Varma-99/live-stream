#!/usr/bin/env bash
# WebRTC/WHEP needs UDP :8000 from Docker → Mac. Colima's default ssh forwarder breaks UDP.
set -euo pipefail

if ! command -v colima >/dev/null 2>&1; then
  exit 0
fi

if ! colima status 2>&1 | rg -qi 'colima is running'; then
  echo "Colima is not running (Docker may use another runtime)."
  exit 0
fi

COLIMA_YAML="${HOME}/.colima/default/colima.yaml"
PF="unknown"
if [[ -f "${COLIMA_YAML}" ]]; then
  PF="$(awk '/^portForwarder:/ {print $2}' "${COLIMA_YAML}" 2>/dev/null || true)"
fi

if [[ "${PF}" == "grpc" ]]; then
  echo "Colima portForwarder=grpc — UDP forwarding OK for SRS WebRTC."
  exit 0
fi

echo ""
echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║  SRS WebRTC/WHEP will NOT work with Colima portForwarder=${PF:-ssh}       ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""
echo "Symptoms: whep.html spins forever, SRS logs show DTLS_HANG / session timeout."
echo "RTMP + FLV/HLS still work — only WebRTC UDP is broken."
echo ""
echo "Fix (one time, ~30s):"
echo "  colima stop"
echo "  colima start --port-forwarder=grpc"
echo "  ./scripts/stop-srs.sh && ./scripts/start-srs.sh"
echo ""
echo "Verify UDP after fix:"
echo "  docker logs srs 2>&1 | tail -5   # retry whep.html — no DTLS_HANG"
echo ""
echo "Playback that works WITHOUT WebRTC fix:"
echo "  http://127.0.0.1:8088/players/srs_player.html?stream=YOUR_KEY.flv&port=8088"
echo ""

exit 1
