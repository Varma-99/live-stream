# k6 QoS load test

Fake viewers hammer join / heartbeat / QoS stats while you watch the **broadcaster war room** graphs.

## Install once

```bash
brew install k6
k6 version
```

## One stream id only (important)

With `videoInput: camera`, the app allows **only one LIVE stream** on the Mac.

Typical mistake:

1. `./load-test/start-stream.sh` → creates **stream 1** → k6 uses `STREAM_ID=1`
2. You open the broadcast page and click **Start** again → creates **stream 2** and ends / replaces **stream 1**
3. k6 still hits stream **1** → join/heartbeat fail (`Stream is not active`) → k6 **stops early** (error threshold)
4. War room may still show old **stream 1** graphs in memory while the UI says **stream 2**

**Fix:** Pick **one** way to start the stream, then match ids:

```bash
./load-test/active-stream.sh   # shows current live id
```

Or let k6 auto-pick (omit `STREAM_ID`):

```bash
k6 run load-test/k6-viewer-qos.js
```

Do **not** start a second stream on the website while k6 is running.

## Run the stack

**Terminal 1 — MediaMTX**

```bash
./scripts/start-mediamtx.sh
```

**Terminal 2 — app**

```bash
./mvnw server config/config-dev.yml
```

**Terminal 3 — start stream + k6**

```bash
chmod +x load-test/start-stream.sh
./load-test/start-stream.sh
# copies STREAM_ID into the k6 command it prints

mkdir -p load-test/results
k6 run --summary-export=load-test/results/k6-summary.json load-test/k6-viewer-qos.js
# auto-picks live stream id; or: --env STREAM_ID=2 after ./load-test/active-stream.sh
```

LAN IP (phones / other machines): use `BASE_URL=http://10.255.61.28:8080`.

## While k6 runs

- **k6 terminal** — latency, error rate, custom `join_ok` / `qos_stats_ok`
- **Broadcaster** — http://127.0.0.1:8080/ui/broadcast.html → QoS war room (live canvas graphs)
- **Ops snapshot:** `curl -s http://127.0.0.1:8080/streams/qos/ops | python3 -m json.tool`

## After k6

- **JSON “report”:** `curl -s http://127.0.0.1:8080/streams/1/postmortem | python3 -m json.tool`
- **UI:** broadcast page → **Postmortem → Refresh analysis**
- **Saved file:** `load-test/results/k6-summary.json` (from last run)

QoS data is **in-memory** — restart the app and postmortem is empty again.

## 200 viewers (default in script)

```bash
./load-test/run-200.sh
# or:
k6 run --env TARGET_VUS=200 --summary-export=load-test/results/k6-200-summary.json load-test/k6-viewer-qos.js
```

Ramp: 60s → 200 VUs, hold 90s, ramp down 40s (~3m20s total).

## 600 viewers

```bash
chmod +x load-test/run-600.sh
./load-test/run-600.sh
```

Ramp: 120s → 600 VUs, hold 180s, ramp down 60s (~6m). Ensure `database.maxSize: 128` in `config-dev.yml` and restart the app first.

## HLS segment load (k6)

Start a stream with **HLS** delivery on the broadcast page, wait ~10s, then:

```bash
chmod +x load-test/hls-bench-suite/*.sh load-test/run-600.sh
./load-test/hls-bench-suite/01-run.sh
# or: k6 run --env TARGET_VUS=20 load-test/hls-load-test.js
```

**What k6 mixes in (viewer-side only):**

| Feature | k6 |
|--------|-----|
| Join / heartbeat / leave | yes |
| Viewer count + room snapshot (`GET /room`) | yes |
| Likes | yes (~8% chance per tick) |
| QoS stats + events (stall, ABR, TTFF, reconnect, recovered, rare fatal) | yes |
| Coupon, pause, degrade, FFmpeg | no — use broadcast UI |

`config-dev.yml` uses `database.maxSize: 32` for this load. Restart the app after changing it.

## Lighter run (50 viewers)

```bash
k6 run --env TARGET_VUS=50 load-test/k6-viewer-qos.js
```

## SRS Phase 5 suite (full load + reports)

**Primary** validation path for SRS. See **`load-test/srs-suite/README.md`**.

```bash
./scripts/start-srs.sh
./mvnw server config/config-dev.yml
# Start ONE broadcast, then:
./load-test/srs-suite/run-all.sh
```

Report: `load-test/reports/srs-phase5-run-*/SRS-PHASE5-REPORT.md`

Legacy 4-test bench: **`load-test/srs-bench-suite/README.md`** (superseded by srs-suite).

```bash
./load-test/srs-bench-suite/00-install-srs-bench.sh   # once (RTMP tools)
```

## k6 WHEP signaling (SRS)

```bash
./load-test/active-stream.sh
k6 run --env STREAM_KEY=<key> load-test/k6-whep-handshake.js
```
