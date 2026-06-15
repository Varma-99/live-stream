# SRS Phase 5 — Load & validation suite

Full stress matrix for **SRS** (not MediaMTX). Produces artifacts under `load-test/reports/` and a combined report.

## One-time setup

```bash
brew install k6
./load-test/srs-bench-suite/00-install-srs-bench.sh   # sb_rtmp_publish + sb_rtmp_load
colima stop && colima start --port-forwarder=grpc      # required for real WHEP/WebRTC
```

## Before every run

```bash
./scripts/start-srs.sh
./mvnw server config/config-dev.yml
```

1. Open http://127.0.0.1:8080/ui/broadcast.html → **Start** (WebRTC delivery)
2. Confirm: `./load-test/active-stream.sh`

## Run everything

```bash
chmod +x load-test/srs-suite/*.sh
./load-test/srs-suite/run-all.sh
```

Report: `load-test/reports/srs-phase5-run-<timestamp>/SRS-PHASE5-REPORT.md`

### Skip tests

```bash
SKIP_TESTS="03-k6-viewers-600,08-whep" ./load-test/srs-suite/run-all.sh
```

## Test matrix

| # | Script | What it stresses | Default limits |
|---|--------|------------------|----------------|
| 00 | `00-preflight.sh` | SRS API, app, k6, srs-bench, Colima | — |
| 01 | `01-baseline-qos.sh` | QoS + SRS API snapshots | 3 × 5s |
| 02 | `02-k6-viewers-200.sh` | k6 (manual only — **not** in run-all) | 200 VUs |
| 03 | `03-k6-viewers-600.sh` | k6 join/heartbeat/QoS — **run-all uses this** | 600 VUs, ~6m |
| 04 | `04-k6-whep-handshake.sh` | k6 WHEP HTTP → SRS via proxy | 50 VUs |
| 05 | `05-rtmp-many-publishers.sh` | 20 concurrent RTMP publishers | 90s |
| 06 | `06-rtmp-many-subscribers.sh` | 100 RTMP read clients | 90s |
| 07 | `07-multi-path-abr.sh` | App ABR _high/_mid/_low on SRS | 12 × 10s |
| 08 | `08-whep-srs-bench.sh` | srs_bench WebRTC + RTMP fallback | 100 subs |
| 09 | `09-degrade-under-load.sh` | degrade/restore under 15 subs | ~2.5m |

## Tunables (env)

```bash
K6_VIEWERS_VUS=200
K6_VIEWERS_MAX_VUS=600
K6_WHEP_VUS=50
RTMP_PUBLISHER_COUNT=20
RTMP_SUBSCRIBER_COUNT=100
WHEP_SUBSCRIBERS=100
DEGRADE_SUBS=15
BASE_URL=http://10.255.51.126:8080
```

## Reports per test

Each test writes `load-test/reports/srs-suite-<name>-<timestamp>/`:

- `suite-meta.json` — stream id/key, SRS API base
- `qos-*.json` — war room snapshot from app API
- `postmortem-*.json` — session postmortem (in-memory; app must stay up)
- `srs-streams-*.json` — SRS `/api/v1/streams/`
- `k6-*-summary.json` — k6 metrics (where applicable)
- `*.log` — bench tool output

Regenerate combined report only:

```bash
python3 load-test/srs-suite/generate-report.py load-test/reports
```

## MediaMTX comparison (later)

When you say go, mirror with:

```bash
MEDIA_BACKEND=mediamtx ./scripts/start-mediamtx.sh
# ... same suite with mediamtx-suite prefix (to be added)
```

For now this suite is **SRS-only**.

## Notes

- **One camera stream** — app allows one LIVE broadcast; bench publishers use separate `benchN` paths (test 05).
- **k6** simulates viewer **API** load (presence/QoS), not real WebRTC video.
- **k6 WHEP** (test 04) hits signaling only; real UDP media needs Colima `grpc` + browser or srs_bench.
- Screenshot war room during test 01 for your write-up.
