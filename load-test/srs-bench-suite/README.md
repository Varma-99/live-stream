# SRS-bench suite (4 tests)

Stress **RTMP ingest**, **WHEP/WebRTC delivery**, and **FFmpeg degrade** while saving reports under `load-test/reports/`.

## Install srs-bench (once)

```bash
./load-test/srs-bench-suite/00-install-srs-bench.sh
```

- **master** → `~/srs-bench` (`sb_rtmp_publish`, `sb_rtmp_load`)
- **feature/rtc** → `~/srs-bench-rtc` (`srs_bench` for WebRTC/WHEP-style load)

**RTMP (required):** Xcode CLI tools + `cd ~/srs-bench && ./configure && make`

**WebRTC (optional):** `brew install go srt` then build `~/srs-bench-rtc`. If this fails, Tests 2 and 4 still run using `sb_rtmp_load` (RTMP play), not WHEP.

## Before every run

```bash
./scripts/start-mediamtx.sh
./mvnw server config/config-dev.yml
```

Start **one** show on http://127.0.0.1:8080/ui/broadcast.html — do not start a second stream mid-test.

```bash
./load-test/active-stream.sh
```

## Run all 4

```bash
./load-test/srs-bench-suite/run-all.sh
```

Or one at a time: `01-baseline.sh` … `04-degrade-whep.sh`

## Reports

Each run creates `load-test/reports/srs-suite-<test>-<timestamp>/` with logs + JSON postmortem.

War room screenshots are manual (one per test).

## Test map

| # | Script | What it stresses |
|---|--------|------------------|
| 1 | `01-baseline.sh` | App FFmpeg + QoS snapshot |
| 2 | `02-whep-100.sh` | 100 WHEP/WebRTC subs (direct + proxy) |
| 3 | `03-rtmp-many.sh` | Many RTMP publishers (extra paths) |
| 4 | `04-degrade-whep.sh` | Degrade/restore + 10 subs |

**Note:** `srs_bench` uses SRS-style `webrtc://` URLs. MediaMTX may differ; scripts also try RTMP subscriber load as fallback. See logs if WebRTC step is skipped.
