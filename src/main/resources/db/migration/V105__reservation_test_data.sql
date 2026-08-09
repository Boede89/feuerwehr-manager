-- flyway:executeInTransaction=false
ALTER TABLE vehicle_reservations
    ADD COLUMN IF NOT EXISTS test_data BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE room_reservations
    ADD COLUMN IF NOT EXISTS test_data BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_vehicle_reservations_test_data ON vehicle_reservations (test_data);
CREATE INDEX IF NOT EXISTS idx_room_reservations_test_data ON room_reservations (test_data);
