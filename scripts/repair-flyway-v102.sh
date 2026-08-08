#!/usr/bin/env bash
# Repariert doppelte/fehlgeschlagene V102-Permissions-Migration und wendet V103 an.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> App stoppen"
docker compose stop app

echo "==> Fehlerhafte Flyway-Einträge zu user_permission_overrides entfernen"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version IN ('102','103') AND (script LIKE '%user_permission_overrides%' OR success=0);"

# Falls jemand fälschlich die Permissions-Datei als V102 angewendet hat und die echte V102 (Fahrzeuge) fehlt:
# Nur den falschen Description-Eintrag löschen, echte vehicle_reservation V102 behalten.
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version='102' AND script LIKE '%user_permission_overrides%';"

echo "==> App neu bauen und starten"
docker compose up -d --build app

echo "==> Kurz warten und Status prüfen"
sleep 12
docker compose ps app
echo "---- letzte App-Logs ----"
docker logs ffm_app --tail 40
