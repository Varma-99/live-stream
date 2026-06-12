#!/usr/bin/env bash
# Publish a test pattern to SRS RTMP (same encoding as MediaMTX WebRTC path).
set -euo pipefail
FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
STREAM_KEY="${1:-phase1test}"
RTMP_URL="rtmp://127.0.0.1:1935/live/${STREAM_KEY}"

if ! command -v "${FFMPEG}" >/dev/null 2>&1; then
  echo "ffmpeg not found at ${FFMPEG}. Install: brew install ffmpeg"
  exit 1
fi

echo "Publishing test pattern to ${RTMP_URL}"
echo "Stop with Ctrl+C."
exec "${FFMPEG}" -y \
  -f lavfi -i testsrc=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=44100 \
  -map 0:v:0 -map 1:a:0 \
  -c:v libx264 -preset ultrafast -tune zerolatency -profile:v baseline -pix_fmt yuv420p \
  -g 15 -keyint_min 15 -sc_threshold 0 -bf 0 \
  -c:a aac -b:a 128k -ar 44100 \
  -muxdelay 0 -muxpreload 0 -flush_packets 1 \
  -f flv -flvflags no_duration_filesize \
  "${RTMP_URL}"
