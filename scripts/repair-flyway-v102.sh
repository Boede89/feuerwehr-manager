#!/usr/bin/env bash
# Behebt V102-Doppelkonflikt und startet mit frischem Image (ohne Build-Cache).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Git-Stand"
git pull --ff-only
ls -1 src/main/resources/db/migration/V102*.sql src/main/resources/db/migration/V103*.sql

echo "==> App stoppen"
docker compose stop app

echo "==> Fehlerhafte Flyway-Einträge zu user_permission_overrides entfernen"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE script LIKE '%user_permission_overrides%';" || true

echo "==> App OHNE Cache neu bauen (wichtig – sonst bleibt alte V102 im JAR)"
docker compose build --no-cache app
docker compose up -d app

echo "==> Kurz warten und Status prüfen"
sleep 15
docker compose ps app
echo "---- letzte App-Logs ----"
docker logs ffm_app --tail 40
