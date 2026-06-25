#!/usr/bin/env bash
# Stop app-tier HAProxy load balancer.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/app-cluster.sh
source "${ROOT}/scripts/lib/app-cluster.sh"

cd "${ROOT}"
app_cluster_require_docker
"${COMPOSE}" -f docker-compose.app-lb.yml down --remove-orphans
echo "App LB stopped."
