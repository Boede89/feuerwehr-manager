#!/usr/bin/env bash
# Feuerwehr-Manager auf dem Server aktualisieren und Container neu starten.
# Nutzung (als root auf dem LXC/Server):
#   cd /opt/feuerwehr-manager && bash scripts/update-server.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  echo "Fehler: docker compose nicht gefunden." >&2
  exit 1
fi

echo "==> Git pull"
git pull --ff-only origin main

echo "==> Container neu bauen und starten"
"${DC[@]}" up -d --build

echo "==> Kurz warten …"
sleep 8

echo "==> Status"
"${DC[@]}" ps

echo ""
echo "==> App-Logs (letzte 40 Zeilen)"
"${DC[@]}" logs app --tail 40 || true

echo ""
echo "Erreichbarkeit:"
echo "  https://$(hostname -I 2>/dev/null | awk '{print $1}')/"
echo "  https://fw-manager.home.arpa/"
echo "  Debug (nur wenn App läuft): http://$(hostname -I 2>/dev/null | awk '{print $1}'):8080/"
