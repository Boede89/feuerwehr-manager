#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> Schema anlegen (korrigierte V72)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v72-schema.sql

echo "==> Flyway V72 als erfolgreich markieren"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v72-mark-success.sql

echo "==> Flyway-Status:"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success, script FROM flyway_schema_history WHERE version='72';"

echo "==> App starten"
docker compose up -d app

echo "==> Warte 45 Sekunden …"
sleep 45

docker compose ps
docker compose logs app --tail 30
