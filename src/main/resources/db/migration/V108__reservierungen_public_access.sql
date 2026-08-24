ALTER TABLE unit_reservierungen_settings
    ADD COLUMN allow_public_reservation BOOLEAN NOT NULL DEFAULT FALSE;
