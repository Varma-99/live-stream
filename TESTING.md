# Testing Guide

## Tiers

| Tier | Location | Command | Needs Docker / apps |
|------|----------|---------|---------------------|
| **Unit** | `src/test/java` | `./mvnw test` | No |
| **Cluster smoke** | `load-test/cluster-suite/` | `PROFILE=quick ./load-test/cluster-suite/run-all.sh` | Yes |
| **SRS load** | `load-test/srs-suite/` | `./load-test/srs-suite/run-all.sh` | Single-app SRS |

## Quick start (full cluster)

**One command (recommended):**

```bash
./scripts/start-cluster.sh
PROFILE=full ./load-test/cluster-suite/run-all.sh
./scripts/stop-cluster.sh
```

**Manual (legacy):**

```bash
# 1. Infra + media
colima start --port-forwarder=grpc
docker-compose -f docker-compose.dev-infra.yml up -d
SRS_STACK=2tier ./scripts/start-srs.sh

# 2. Two app instances (separate terminals)
./mvnw -Dexec.args="server config/config-dev-pg-app1.yml" compile exec:java
./mvnw -Dexec.args="server config/config-dev-pg-app2.yml" compile exec:java

# 3. Load balancer
./scripts/start-app-lb.sh

# 4. Status check
./scripts/app-cluster-status.sh

# 5. Run tests
PROFILE=quick ./load-test/cluster-suite/run-all.sh
```

## Profiles

| Profile | Tests | ~Time |
|---------|-------|-------|
| `quick` | 00–04, 06 | 2–3 min |
| `ci` | quick + 05, 07 | 5–8 min |
| `full` | ci + 08–10 | **20–35 min** (k6 alone ~6 min at 600 VUs) |

## Unit test coverage

- `StreamControlServiceTest` — lock, forward, local stop
- `InternalApiAuthTest` — internal API token
- `PeerInstanceClientTest` — peer lookup
- QoS, WHEP path, FFmpeg builders

## Known limitations (documented by suite)

1. **Orphan FFmpeg** — if app-1 crashes, its FFmpeg may survive; app-2 cannot kill remote processes.
2. **SRS forward SPOF** — single `srs-playback-1` forward target; kill playback breaks WHEP until republish.
3. **FFmpeg watchdog** — test `09` documents gap: dead FFmpeg may still show `publishActive`.
4. **Redis restart** — grace period (30s) prevents false zombie stops; owner keys repopulate on refresh.

## Reports

- Cluster: `load-test/reports/cluster-run-*/CLUSTER-SUITE-REPORT.md`
- SRS load: `load-test/reports/srs-phase5-run-*/SRS-PHASE5-REPORT.md`

## Teardown

```bash
./scripts/stop-cluster.sh              # everything
./scripts/stop-cluster.sh --keep-infra # apps + SRS only (faster restart)
```

Legacy:

```bash
./scripts/stop-app-lb.sh
pkill -f 'config-dev-pg-app' || true
./scripts/stop-srs.sh
docker-compose -f docker-compose.dev-infra.yml down
```
