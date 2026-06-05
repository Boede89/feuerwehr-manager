#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> Schema anlegen (Duplicate-Fehler sind unkritisch)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-schema.sql \
  2>/dev/null || true

echo "==> Flyway V37 als erfolgreich markieren (Migration nicht erneut ausführen)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-mark-success.sql

echo "==> Flyway-Status:"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success, script FROM flyway_schema_history WHERE version='37';"

echo "==> App starten"
docker compose up -d app

echo "==> Warte 40 Sekunden …"
sleep 40

docker compose ps
docker compose logs app --tail 30
