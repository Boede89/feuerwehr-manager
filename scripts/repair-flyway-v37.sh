#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> Fehlgeschlagenen Flyway-Eintrag V37 entfernen"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37';"

echo "==> Schema manuell anlegen (Fehler bei bereits vorhandenen Objekten ignorieren)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-manual.sql \
  || true

echo "==> Sicherheitshalber V37-Eintrag erneut entfernen"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37';"

echo "==> Neu bauen und starten"
docker compose up -d --build app

echo "==> Warte 40 Sekunden …"
sleep 40

docker compose ps
docker compose logs app --tail 30
