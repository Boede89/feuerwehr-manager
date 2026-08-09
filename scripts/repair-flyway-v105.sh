#!/usr/bin/env bash
# Repariert fehlgeschlagene V105 (reservation test_data).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> 1) Git aktualisieren"
git fetch origin
git reset --hard origin/main

echo "==> 2) App stoppen"
docker compose stop app

echo "==> 3) Flyway V105 vor Reparatur:"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script, checksum FROM flyway_schema_history WHERE version='105';" \
  || true

echo "==> 4) Schema anlegen (idempotent)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v105-schema.sql

echo "==> 5) Fehlgeschlagenen Flyway-Eintrag entfernen"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v105-mark-success.sql

echo "==> 6) Flyway V105 nach Reparatur:"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script, checksum FROM flyway_schema_history WHERE version='105';" \
  || true

echo "==> 7) App neu bauen und starten"
docker compose up -d --build app

echo "==> 8) Warten und Logs"
sleep 30
docker compose ps app
echo "---- Logs ----"
docker compose logs app --tail 40
