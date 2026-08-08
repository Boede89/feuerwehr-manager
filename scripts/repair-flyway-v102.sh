#!/usr/bin/env bash
# Repariert fehlgeschlagene V102 (vehicle reservation multi vehicles) wie V72.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> 1) Git aktualisieren"
git fetch origin
git reset --hard origin/main
rm -f src/main/resources/db/migration/V102__user_permission_overrides.sql

echo "==> 2) App stoppen"
docker compose stop app

echo "==> 3) Flyway V102 vor Reparatur:"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script, checksum FROM flyway_schema_history WHERE version='102';" \
  || true

echo "==> 4) Schema anlegen (idempotent)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v102-schema.sql

echo "==> 5) Flyway V102 als erfolgreich markieren"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v102-mark-success.sql

echo "==> 6) Flyway V102 nach Reparatur (success muss 1 sein):"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script, checksum FROM flyway_schema_history WHERE version='102';"

echo "==> 7) App neu bauen und starten"
docker compose build --no-cache app
docker compose up -d app

echo "==> 8) Warten und Logs"
sleep 25
docker compose ps app
echo "---- Logs ----"
docker logs ffm_app --tail 40
