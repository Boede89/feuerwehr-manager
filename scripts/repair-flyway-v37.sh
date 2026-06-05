#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> Fehlgeschlagenen Flyway-Eintrag V37 entfernen"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37';"

echo "==> Neu bauen und starten"
docker compose up -d --build app

echo "==> Warte 35 Sekunden …"
sleep 35

docker compose ps
docker compose logs app --tail 25
