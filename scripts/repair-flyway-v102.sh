#!/usr/bin/env bash
# Notfall-Fix: doppelte Flyway-V102 entfernen und Image ohne Cache neu bauen.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> 1) Git aktualisieren"
git fetch origin
git reset --hard origin/main

echo "==> 2) Alte Doppel-Migration sicher entfernen (falls noch vorhanden)"
rm -f src/main/resources/db/migration/V102__user_permission_overrides.sql

echo "==> 3) Erwartete Dateien"
ls -1 src/main/resources/db/migration/V102*.sql
ls -1 src/main/resources/db/migration/V103*.sql

if [[ -f src/main/resources/db/migration/V102__user_permission_overrides.sql ]]; then
  echo "FEHLER: V102__user_permission_overrides.sql existiert noch!"
  exit 1
fi
if [[ ! -f src/main/resources/db/migration/V103__user_permission_overrides.sql ]]; then
  echo "FEHLER: V103__user_permission_overrides.sql fehlt!"
  exit 1
fi

echo "==> 4) Flyway-Altlasten zu Permissions entfernen"
docker compose stop app || true
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE script LIKE '%user_permission_overrides%';" || true

echo "==> 5) Image OHNE Cache neu bauen (dauert einige Minuten)"
docker compose build --no-cache --pull app

echo "==> 6) Prüfen, dass im Image keine doppelte V102-Permissions-Datei steckt"
TMP_CID=$(docker create feuerwehr-manager-app)
docker cp "$TMP_CID:/app/app.jar" /tmp/ffm-check.jar
docker rm "$TMP_CID" >/dev/null
jar tf /tmp/ffm-check.jar | grep 'db/migration/V102' || true
jar tf /tmp/ffm-check.jar | grep 'db/migration/V103' || true
if jar tf /tmp/ffm-check.jar | grep -q 'V102__user_permission_overrides'; then
  echo "FEHLER: Alte V102-Permissions-Datei ist noch im JAR!"
  exit 1
fi

echo "==> 7) App starten"
docker compose up -d app
sleep 15
docker compose ps app
echo "---- Logs ----"
docker logs ffm_app --tail 30
