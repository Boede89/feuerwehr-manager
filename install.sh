#!/usr/bin/env bash
# Ein-Befehl-Installation — delegiert an scripts/install-server.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${ROOT}/scripts/install-server.sh" --in-repo --skip-docker "$@"
