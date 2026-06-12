#!/usr/bin/env bash
set -euo pipefail
DIR="$(dirname "$0")"
"${DIR}/stop-mediamtx.sh"
exec "${DIR}/start-mediamtx.sh"
