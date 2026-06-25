# Cluster Test Suite

Multi-instance control plane tests (Redis, cross-instance stop, HAProxy LB, SRS 2-tier).

## Prerequisites

```bash
colima start --port-forwarder=grpc
docker-compose -f docker-compose.dev-infra.yml up -d
SRS_STACK=2tier ./scripts/start-srs.sh
```

**Terminal 1 — app-1:**
```bash
./mvnw -Dexec.args="server config/config-dev-pg-app1.yml" compile exec:java
```

**Terminal 2 — app-2:**
```bash
./mvnw -Dexec.args="server config/config-dev-pg-app2.yml" compile exec:java
```

**LB:**
```bash
./scripts/start-app-lb.sh
```

## Run suite

```bash
# Start full stack (one terminal):
./scripts/start-cluster.sh

chmod +x load-test/cluster-suite/*.sh load-test/cluster-suite/generate-report.py
PROFILE=quick ./load-test/cluster-suite/run-all.sh    # ~2 min
PROFILE=ci    ./load-test/cluster-suite/run-all.sh    # + WHEP + Redis grace
PROFILE=full  ./load-test/cluster-suite/run-all.sh    # + chaos + k6 (600 VUs, ~6 min)

./scripts/stop-cluster.sh
```

Skip tests: `SKIP_TESTS=08-srs-playback-kill PROFILE=full ./load-test/cluster-suite/run-all.sh`

## Unit tests (no infra)

```bash
./mvnw test
```

## Test matrix

| # | Script | Profile | What it proves |
|---|--------|---------|----------------|
| 00 | preflight | quick+ | PG, Redis, SRS, app-1/2, LB |
| 01 | lb-lifecycle | quick+ | pause/resume/stop via LB |
| 02 | cross-instance-broadcaster | quick+ | HB on app-2, stream on app-1 |
| 03 | cross-instance-stop | quick+ | bidirectional forwarded stop |
| 04 | viewer-presence | quick+ | Redis ZSET + join across instances |
| 05 | whep-2tier | ci+ | origin → playback → WHEP 201 |
| 06 | lb-round-robin | quick+ | both instances in rotation |
| 07 | redis-restart-grace | ci+ | no false zombie on Redis restart |
| 08 | srs-playback-kill | full | SRS forward SPOF |
| 09 | ffmpeg-watchdog | full | documents FFmpeg watchdog gap |
| 10 | k6-presence | full | **600 VUs** join/HB/QoS/likes via LB (~6 min, needs k6) |

Report: `load-test/reports/cluster-run-<timestamp>/CLUSTER-SUITE-REPORT.md`

## Known limitations

See [TESTING.md](../../TESTING.md) at repo root.
