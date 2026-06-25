# Cluster Suite Report

- **Run ID:** `20260623-160339`
- **Profile:** `full`
- **Generated:** 2026-06-23T10:42:00.263491+00:00

| Test | Status | Message |
|------|--------|---------|
| `00-preflight-20260623-143633` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-143634` | **UNKNOWN** |  |
| `00-preflight-20260623-143853` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-143853` | **UNKNOWN** |  |
| `02-cross-instance-broadcaster-20260623-143855` | **UNKNOWN** |  |
| `03-cross-instance-stop-20260623-143855` | **UNKNOWN** |  |
| `04-viewer-presence-20260623-143856` | **UNKNOWN** |  |
| `05-whep-2tier-20260623-143857` | **PASS** | origin → playback forward → WHEP LB |
| `06-lb-round-robin-20260623-143903` | **PASS** | LB routes to app-1 and app-2 |
| `07-redis-restart-grace-20260623-143906` | **UNKNOWN** |  |
| `00-preflight-20260623-144330` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-144331` | **UNKNOWN** |  |
| `01-lb-lifecycle-20260623-153142` | **UNKNOWN** |  |
| `01-lb-lifecycle-20260623-153624` | **PASS** | pause/resume/stop via LB synced with Redis |
| `00-preflight-20260623-153741` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-153743` | **PASS** | pause/resume/stop via LB synced with Redis |
| `02-cross-instance-broadcaster-20260623-153745` | **UNKNOWN** |  |
| `02-cross-instance-broadcaster-20260623-154135` | **PASS** | heartbeat via app-2 keeps stream alive on app-1 |
| `00-preflight-20260623-154235` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-154236` | **PASS** | pause/resume/stop via LB synced with Redis |
| `02-cross-instance-broadcaster-20260623-154238` | **PASS** | heartbeat via app-2 keeps stream alive on app-1 |
| `03-cross-instance-stop-20260623-154245` | **PASS** | bidirectional cross-instance stop |
| `04-viewer-presence-20260623-154250` | **PASS** | join/heartbeat/leave across instances |
| `05-whep-2tier-20260623-154253` | **PASS** | origin → playback forward → WHEP LB |
| `06-lb-round-robin-20260623-154257` | **PASS** | LB routes to app-1 and app-2 |
| `07-redis-restart-grace-20260623-154258` | **PASS** | no false zombie stop during Redis restart grace |
| `08-srs-playback-kill-20260623-154332` | **PASS** | playback kill breaks forward (documented SPOF) |
| `09-ffmpeg-watchdog-20260623-154407` | **PASS** | FFmpeg death detected by app |
| `10-k6-presence-20260623-154424` | **UNKNOWN** |  |
| `10-k6-presence-20260623-155508` | **UNKNOWN** |  |
| `00-preflight-20260623-155525` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-155527` | **PASS** | pause/resume/stop via LB synced with Redis |
| `02-cross-instance-broadcaster-20260623-155528` | **PASS** | heartbeat via app-2 keeps stream alive on app-1 |
| `03-cross-instance-stop-20260623-155534` | **PASS** | bidirectional cross-instance stop |
| `04-viewer-presence-20260623-155538` | **PASS** | join/heartbeat/leave across instances |
| `05-whep-2tier-20260623-155541` | **PASS** | origin → playback forward → WHEP LB |
| `06-lb-round-robin-20260623-155545` | **PASS** | LB routes to app-1 and app-2 |
| `07-redis-restart-grace-20260623-155546` | **UNKNOWN** |  |
| `08-srs-playback-kill-20260623-155650` | **UNKNOWN** |  |
| `00-preflight-20260623-160004` | **FAIL** | preflight checks failed |
| `01-lb-lifecycle-20260623-160019` | **UNKNOWN** |  |
| `00-preflight-20260623-160339` | **PASS** | all cluster components healthy |
| `01-lb-lifecycle-20260623-160340` | **PASS** | pause/resume/stop via LB synced with Redis |
| `02-cross-instance-broadcaster-20260623-160342` | **PASS** | heartbeat via app-2 keeps stream alive on app-1 |
| `03-cross-instance-stop-20260623-160348` | **PASS** | bidirectional cross-instance stop |
| `04-viewer-presence-20260623-160353` | **PASS** | join/heartbeat/leave across instances |
| `05-whep-2tier-20260623-160357` | **PASS** | origin → playback forward → WHEP LB |
| `06-lb-round-robin-20260623-160401` | **PASS** | LB routes to app-1 and app-2 |
| `07-redis-restart-grace-20260623-160402` | **PASS** | no false zombie stop during Redis restart grace |
| `08-srs-playback-kill-20260623-160435` | **PASS** | playback kill breaks forward (documented SPOF) |
| `09-ffmpeg-watchdog-20260623-160509` | **PASS** | FFmpeg death detected by app |
| `10-k6-presence-20260623-160525` | **UNKNOWN** |  |

## Per-test artifacts

- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-07-redis-restart-grace-20260623-155546`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-08-srs-playback-kill-20260623-155650`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-00-preflight-20260623-160004`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-01-lb-lifecycle-20260623-160019`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-00-preflight-20260623-160339`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-01-lb-lifecycle-20260623-160340`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-02-cross-instance-broadcaster-20260623-160342`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-03-cross-instance-stop-20260623-160348`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-04-viewer-presence-20260623-160353`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-05-whep-2tier-20260623-160357`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-06-lb-round-robin-20260623-160401`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-07-redis-restart-grace-20260623-160402`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-08-srs-playback-kill-20260623-160435`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-09-ffmpeg-watchdog-20260623-160509`
- `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports/cluster-suite-10-k6-presence-20260623-160525`
