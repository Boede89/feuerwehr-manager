#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> V72-Reste entfernen und fehlgeschlagenen Flyway-Eintrag löschen"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v72-cleanup.sql

echo "==> Flyway-Status (V72 sollte fehlen):"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success, script FROM flyway_schema_history WHERE version='72';" || true

echo "==> App starten (V72 wird neu ausgeführt)"
docker compose up -d app

echo "==> Warte 45 Sekunden …"
sleep 45

docker compose ps
docker compose logs app --tail 40
