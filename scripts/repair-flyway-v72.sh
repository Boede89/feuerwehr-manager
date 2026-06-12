#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> App stoppen (Restart-Schleife unterbrechen)"
docker compose stop app

echo "==> Flyway V72 vor Reparatur:"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script FROM flyway_schema_history WHERE version='72';" \
  2>/dev/null || true

echo "==> Schema anlegen (korrigierte V72)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v72-schema.sql

echo "==> Flyway V72 als erfolgreich markieren (SQL-Typ + Checksum)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v72-mark-success.sql

echo "==> Flyway V72 nach Reparatur:"
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT installed_rank, version, success, type, script, checksum FROM flyway_schema_history WHERE version='72';"

echo "==> App neu bauen und starten (korrigierte V72 im JAR)"
docker compose up -d --build app

echo "==> Warte 60 Sekunden …"
sleep 60

docker compose ps
docker compose logs app --tail 25
