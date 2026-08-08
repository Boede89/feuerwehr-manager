-- Schema für V102 (vehicle reservation multi vehicles), idempotent
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
