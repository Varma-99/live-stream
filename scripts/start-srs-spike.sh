#!/usr/bin/env bash
# Deprecated — use ./scripts/start-srs.sh (Phase 2)
echo "Note: start-srs-spike.sh is deprecated. Using start-srs.sh…" >&2
exec "$(dirname "$0")/start-srs.sh"
