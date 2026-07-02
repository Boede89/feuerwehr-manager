ALTER TABLE unit_berichte_settings
    ADD COLUMN einsatz_release_print_maengel BOOLEAN NOT NULL DEFAULT FALSE AFTER einsatz_release_print_geraetewart;
