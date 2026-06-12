#!/usr/bin/env bash
# Start SRS (Docker) then the Dropwizard app in SRS mode.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== 1/2 Media server (SRS) ==="
"$ROOT/scripts/start-srs.sh"

echo ""
echo "=== 2/2 App (config-dev.yml, streamDelivery=srs) ==="
echo "    Viewer:  http://localhost:8080/ui/viewer.html"
echo "    Stream:  http://localhost:8080/ui/broadcast.html"
exec ./mvnw server config/config-dev.yml
