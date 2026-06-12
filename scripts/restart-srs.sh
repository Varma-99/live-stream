#!/usr/bin/env bash
set -euo pipefail
DIR="$(dirname "$0")"
"${DIR}/stop-srs.sh"
exec "${DIR}/start-srs.sh"
