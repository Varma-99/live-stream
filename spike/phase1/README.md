# Phase 1 — SRS spike (Docker CLI + Colima)

Prove SRS works locally before touching the Dropwizard app.

## What you need (already on this Mac)

| Tool | Status | Install if missing |
|------|--------|-------------------|
| ffmpeg | `brew install ffmpeg` | test pattern publish |
| docker CLI | `brew install docker` | run SRS container |
| colima | `brew install colima` | Docker daemon (no Desktop needed) |
| mediamtx | optional | **must be stopped** — shares port 1935 |

**No new brew packages required** if ffmpeg, docker, and colima are already installed.

## Quick run

```bash
# 1. Ensure Docker daemon is up
colima start

# 2. Stop MediaMTX if running (port 1935 conflict)
./scripts/stop-mediamtx.sh

# 3. Start SRS
./scripts/start-srs-spike.sh

# 4. Publish test stream (separate terminal)
./scripts/publish-srs-spike.sh

# 5. Play in browser (FLV — works on Docker Mac; note port=8088!)
open "http://127.0.0.1:8088/players/srs_player.html?stream=phase1test.flv&port=8088"
# Or paste manually: http://127.0.0.1:8088/live/phase1test.flv

# WHEP (may hang on Docker Mac due to UDP — optional)
open "http://127.0.0.1:8088/players/whep.html?stream=phase1test"

# 6. Stop
./scripts/stop-srs-spike.sh
```

## Pass criteria

- [ ] SRS container starts without errors
- [ ] FFmpeg publishes to `rtmp://127.0.0.1:1935/live/phase1test`
- [ ] WHEP player shows video + audio
- [ ] Latency feels comparable to MediaMTX (~1–3s on localhost)

## Notes

- `rtmp_to_rtc` is **on** in `srs-spike.conf` (off in stock `rtc.conf`).
- SRS players are on host **8088** (container 8080) to avoid clashing with the app on 8080.
- For LAN phones later, set `SRS_CANDIDATE=<your-en0-ip>` before `start-srs-spike.sh`.
