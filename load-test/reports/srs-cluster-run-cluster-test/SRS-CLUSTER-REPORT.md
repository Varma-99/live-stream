# SRS Edge Cluster — Test Report

Generated: 2026-06-16T07:59:36.109964+00:00
Run id: `cluster-test`

## Test runs included

Found **0** report directories under `/Users/macharla.h/projects/live-stream-sandbox/load-test/reports`.

| Test | Directory |
|------|-----------|

## Details

## How to re-run

```bash
colima start --port-forwarder=grpc
SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh
./mvnw server config/config-dev-cluster.yml
./load-test/srs-suite/run-all-cluster.sh
```

