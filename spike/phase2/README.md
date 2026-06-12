# Phase 2 — SRS infrastructure

Repeatable SRS ops (config + Docker Compose + scripts). **No app code changes yet.**

## Prerequisites

- `brew install docker docker-compose colima ffmpeg`
- `colima start` (or `colima restart` if `docker info` fails)

## Start / stop

```bash
# If MediaMTX is running (native mediamtx process):
./scripts/stop-mediamtx.sh

# If SRS was already running, or port 1935 shows COMMAND=ssh (Colima):
./scripts/stop-srs.sh

./scripts/start-srs.sh
./scripts/publish-srs-test.sh
./scripts/stop-srs.sh
```

Restart: `./scripts/restart-srs.sh`

## Files

| File | Role |
|------|------|
| `srs.conf` | RTMP + `rtmp_to_rtc` + HTTP-FLV + HLS + API :1985 |
| `docker-compose.srs.yml` | Container `srs`, port map, `SRS_CANDIDATE` env |
| `scripts/start-srs.sh` | Compose up, health check |
| `scripts/stop-srs.sh` | Compose down + remove legacy `srs-spike` |
| `scripts/publish-srs-test.sh` | Manual FFmpeg test publish |

## Ports (host)

- `1935` RTMP ingest
- `1985` HTTP API + WHEP signaling
- `8088` players + HTTP-FLV + HLS (maps container 8080)
- `8000` / `10080` UDP — WebRTC media

## Smoke test

```bash
ffplay rtmp://127.0.0.1:1935/live/phase1test
open "http://127.0.0.1:8088/players/srs_player.html?stream=phase1test.flv&port=8088"
curl -s http://127.0.0.1:1985/api/v1/streams/ | python3 -m json.tool
```

## LAN WebRTC (optional)

```bash
SRS_CANDIDATE=$(ipconfig getifaddr en0) ./scripts/start-srs.sh
```

WHEP in browser via Docker on Mac may still fail (UDP). Use ffplay or FLV player for local proof.

## Phase 1 scripts

`start-srs-spike.sh` / `publish-srs-spike.sh` remain for reference. **Use `start-srs.sh` going forward.**

## App integration (Phase 3+4)

```bash
./scripts/start-srs.sh
./mvnw server config/config-srs.yml
# broadcast.html → Start stream
# viewer.html → Watch (WHEP via /whep/... on :8080)
```

`config-dev.yml` still uses MediaMTX unchanged.
