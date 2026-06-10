-- Testmodus: Schattenkopien für Fahrzeuge und Räume

ALTER TABLE vehicles
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_vehicle_production_source FOREIGN KEY (production_source_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_vehicle_production_shadow (production_source_id);

ALTER TABLE rooms
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_room_production_source FOREIGN KEY (production_source_id) REFERENCES rooms (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_room_production_shadow (production_source_id);
