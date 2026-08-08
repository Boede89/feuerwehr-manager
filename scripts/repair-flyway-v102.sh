#!/usr/bin/env bash
# Repariert Migration V102 (user_permission_overrides) nach Fehlschlag oder Checksum-Mismatch.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> App stoppen"
docker compose stop app

echo "==> Flyway-Eintrag V102 entfernen (fehlgeschlagen oder veraltete Checksum)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version='102';"

echo "==> App neu bauen und starten (Flyway wendet V102 erneut an, idempotent)"
docker compose up -d --build app

echo "==> Kurz warten und Status prüfen"
sleep 12
docker compose ps app
echo "---- letzte App-Logs ----"
docker logs ffm_app --tail 60
