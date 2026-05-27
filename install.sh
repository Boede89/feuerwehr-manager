#!/usr/bin/env bash
# Ein-Befehl-Installation nach Git-Clone (im Repository-Root ausführen).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "Fehler: Docker ist nicht installiert oder nicht im PATH."
  echo "Bitte zuerst Docker + Docker Compose Plugin installieren (siehe docs/PROXMOX-RATGEBER.md)."
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "Fehler: Weder 'docker compose' noch 'docker-compose' gefunden."
  exit 1
fi

echo "Starte MySQL + Feuerwehr-Manager (Build kann einige Minuten dauern)..."
"${COMPOSE[@]}" up -d --build

echo ""
echo "Fertig."
echo "  Web-Oberfläche:  http://localhost:8080"
echo "  Divera-Setup:    http://localhost:8080/settings/divera?unit=1"
echo "  Hinweise:       docs/SCHRITT-FUER-SCHRITT.md"
