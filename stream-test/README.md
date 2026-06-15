# Stream test — results and guide

This folder documents the **4-step streaming stress suite** (FFmpeg + srs-bench + QoS reports) for the live-stream project.

Scripts live in `load-test/srs-bench-suite/`. Raw artifacts are written under `load-test/reports/srs-suite-*`.

---

## Your run (4 Jun 2026)

| Field | Value |
|--------|--------|
| Stream id | **2** |
| Stream key | `SHfNVvhoX_N8TWux3mG__pDJtxtBoTQE` |
| Title | llll |
| Suite | `./load-test/srs-bench-suite/run-all.sh` |

### Summary

| Test | Status | What happened |
|------|--------|----------------|
| **1 — Baseline** | **Pass** | QoS + postmortem saved while FFmpeg was live. |
| **2 — 100 subscribers** | **Partial** | WebRTC bench skipped (not built). **RTMP load did not run** — macOS has no `timeout` command (see below). Before/after JSON still saved. |
| **3 — Many RTMP** | **Pass** | 15 extra publishers on `bench1_high` … `bench15_high` for 60s. |
| **4 — Degrade / restore** | **Pass** | API degrade → `encodeDegraded: true`, restore → `false`. 10 RTMP subscribers in background. QoS snapshots + postmortem saved. |

**Overall:** Ingest/encode/degrade pipeline behaved correctly. Test 2 delivery load should be **re-run** after pulling latest scripts (`run_with_timeout` fix) or `brew install coreutils` for `gtimeout`.

---

## Report folders from your run

```
load-test/reports/srs-suite-01-baseline-20260604-151726/
  qos-baseline.json
  postmortem-baseline.json
  README.txt

load-test/reports/srs-suite-02-whep-100-20260604-151730/
  qos-before-load.json
  qos-after-load.json
  postmortem-after-load.json
  sb-rtmp-load.log          ← only "timeout: command not found"
  srs-bench-webrtc.log      ← skipped (optional tool)
  targets.txt
  whep-http-endpoints.txt

load-test/reports/srs-suite-03-rtmp-many-20260604-151756/
  publishers.txt
  publish-1.log … publish-15.log
  mediamtx-paths-before.json / after.json (if API up)
  qos-after-rtmp-many.json
  postmortem-after-rtmp-many.json

load-test/reports/srs-suite-04-degrade-20260604-151914/
  qos-before-degrade.json
  qos-after-degrade.json
  qos-after-restore.json
  postmortem-after-degrade-test.json
  degrade-response.json
  restore-response.json
  sb-rtmp-load.log (background subscribers)
```

Open JSON with:

```bash
python3 -m json.tool load-test/reports/srs-suite-01-baseline-20260604-151726/postmortem-baseline.json
```

War room screenshots are manual (one per test on the broadcast page).

---

## Test 1 — FFmpeg baseline

**Purpose:** Confirm the real path works before load: **camera/test → FFmpeg → 3× RTMP → MediaMTX → QoS war room**.

**What we saved:**

- `qos-baseline.json` — ingest/encode/delivery/viewer scores at that moment
- `postmortem-baseline.json` — timeline, health scores, viewer sessions (if any)

**How to read:** Ingest and encode should be high (~100) when FFmpeg is publishing. Delivery/viewer scores reflect k6 or real viewers from earlier runs, not srs-bench.

---

## Test 2 — 100 subscribers (delivery)

**Purpose:** Stress **playback** — many clients reading the same stream.

**Planned:**

1. Optional `srs_bench` WebRTC (100 players) — **skipped** (WebRTC branch not built on Mac).
2. `sb_rtmp_load -c 100` for 90s on  
   `rtmp://127.0.0.1:1935/live/{streamKey}_high`

**What actually ran:** Step 2 failed immediately because macOS does not ship GNU `timeout`. The log only contains:

```text
timeout: command not found
```

So **no 100-subscriber load** was applied in this run. QoS before/after files are still useful as snapshots but do not show load impact.

**Re-run Test 2:**

```bash
./load-test/srs-bench-suite/02-whep-100.sh
```

(Scripts now use `run_with_timeout`, which works on macOS via `perl`.)

**Real WHEP check (browser):**

`http://127.0.0.1:8080/ui/viewer.html?stream=2`

---

## Test 3 — Many RTMP publishers

**Purpose:** Stress **MediaMTX ingest** with multiple independent RTMP paths (not your app’s stream key).

**What ran:** 15 virtual publishers → `rtmp://127.0.0.1:1935/live/bench1_high` … `bench15_high` for **60 seconds**.

**Why separate paths:** Only one publisher should use your live `{streamKey}_high` (FFmpeg). Extra paths test MediaMTX capacity without fighting the broadcaster.

**How to read:**

- `publish-*.log` — per-publisher srs-bench output
- `mediamtx-paths-*.json` — path list from MediaMTX API (`:9997`)
- Your stream’s war room **ingest** may stay tied to stream **2** only; bench paths are mainly a **server** stress test

---

## Test 4 — Degrade / restore under load

**Purpose:** Broadcaster **Degrade** / **Restore** while subscribers are playing.

**What ran:**

1. **10** `sb_rtmp_load` clients on your `_high` RTMP URL (background).
2. `POST /streams/2/degrade` → JSON shows `"encodeDegraded": true` (FFmpeg restarts with lower quality).
3. Wait 30s.
4. `POST /streams/2/restore-quality` → `"encodeDegraded": false`.
5. Wait 30s; save QoS + postmortem.

**How to read:**

- `degrade-response.json` / `restore-response.json` — API OK, ladder URLs unchanged
- Compare `qos-before-degrade.json` vs `qos-after-degrade.json` vs `qos-after-restore.json` for encode FPS/bitrate and ingest
- `postmortem-after-degrade-test.json` — full timeline for the session

**Viewer note:** RTMP subscribers are not the same as browser WHEP viewers; use the viewer page to validate auto-reconnect after degrade.

---

## Tools used

| Tool | Role |
|------|------|
| **App + FFmpeg** | Real encode (Tests 1, 4; publisher for Test 2) |
| **MediaMTX** | RTMP `:1935`, WHEP `:8889`, API `:9997` |
| **srs-bench (master)** | `sb_rtmp_load`, `sb_rtmp_publish` in `~/srs-bench` |
| **srs-bench (rtc)** | Optional `srs_bench` — not built (needs `brew install srt` + Go) |
| **k6** | Separate HTTP/QoS load test (already run on branch `k6`) |

---

## How to run again

```bash
./scripts/start-mediamtx.sh
./mvnw server config/config-dev.yml
# Start ONE show on broadcast page
./load-test/active-stream.sh
./load-test/srs-bench-suite/run-all.sh
```

Install srs-bench (once):

```bash
./load-test/srs-bench-suite/00-install-srs-bench.sh
```

---

## Interpreting health scores (war room / postmortem)

| Score | Meaning |
|-------|--------|
| **Ingest ~100** | FFmpeg → MediaMTX upload healthy |
| **Encode ~100** | FFmpeg keeping FPS; few errors/restarts |
| **Delivery lower** | Often synthetic k6 events or RTMP bench, not necessarily broken WebRTC |
| **Overall ~90+** | Weighted ingest + encode + viewer; delivery row can be low independently |
| **Root cause: delivery** | Many stall/reconnect/ABR **events** in timeline (often from k6 labels) |

---

## Known issues (fixed or optional)

1. **`timeout: command not found`** — Fixed in `load-test/lib/common.sh` (`run_with_timeout`). Re-run Test 2.
2. **WebRTC bench not built** — Optional; Tests 2/4 use RTMP `sb_rtmp_load` instead.
3. **Second stream on website** — Only one live stream at a time with camera; always match `STREAM_ID` from `active-stream.sh`.
4. **Reports not in DB** — JSON is in-memory for that app session; copy `load-test/reports/` before restarting the server.

---

## Related

- k6 load test: `load-test/README.md`, branch `k6`
- QoS war room: `http://127.0.0.1:8080/ui/broadcast.html`
- LAN viewers: `http://10.255.51.126:8080/ui/viewer.html?stream=2`
