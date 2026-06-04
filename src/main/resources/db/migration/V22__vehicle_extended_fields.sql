ALTER TABLE vehicles
    ADD COLUMN vehicle_type VARCHAR(32) NULL DEFAULT 'lkw',
    ADD COLUMN license_plate VARCHAR(20) NULL,
    ADD COLUMN year_built INT NULL,
    ADD COLUMN phone VARCHAR(50) NULL,
    ADD COLUMN length_m DECIMAL(8, 2) NULL,
    ADD COLUMN width_m DECIMAL(8, 2) NULL,
    ADD COLUMN height_m DECIMAL(8, 2) NULL,
    ADD COLUMN weight_kg INT NULL,
    ADD COLUMN service_status VARCHAR(32) NOT NULL DEFAULT 'aktiv',
    ADD COLUMN notes TEXT NULL;

UPDATE vehicles SET service_status = IF(active = 1, 'aktiv', 'ausser_dienst') WHERE service_status = 'aktiv' AND active = 0;
