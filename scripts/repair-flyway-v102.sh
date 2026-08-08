#!/usr/bin/env bash
# Repariert fehlgeschlagene V102 (vehicle reservation multi vehicles) + startet App.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Git aktualisieren"
git fetch origin
git reset --hard origin/main
rm -f src/main/resources/db/migration/V102__user_permission_overrides.sql

echo "==> App stoppen"
docker compose stop app

echo "==> Fehlgeschlagenen Flyway-Eintrag V102 entfernen"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version='102' AND success=0;"

echo "==> Schema für V102 vorbereiten (idempotent)"
docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager <<'SQL'
CREATE TABLE IF NOT EXISTS vehicle_reservation_vehicles (
    reservation_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (reservation_id, vehicle_id),
    CONSTRAINT fk_vrv_reservation FOREIGN KEY (reservation_id) REFERENCES vehicle_reservations(id) ON DELETE CASCADE,
    CONSTRAINT fk_vrv_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    INDEX idx_vrv_vehicle (vehicle_id)
);
INSERT INTO vehicle_reservation_vehicles (reservation_id, vehicle_id, sort_order)
SELECT vr.id, vr.vehicle_id, 0
FROM vehicle_reservations vr
WHERE vr.vehicle_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_reservation_vehicles vrv
      WHERE vrv.reservation_id = vr.id AND vrv.vehicle_id = vr.vehicle_id
  );
SQL

echo "==> App neu bauen und starten"
docker compose build --no-cache app
docker compose up -d app

echo "==> Warten..."
sleep 20
docker compose ps app
echo "---- Logs ----"
docker logs ffm_app --tail 40
