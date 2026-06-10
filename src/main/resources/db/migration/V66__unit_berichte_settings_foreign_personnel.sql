ALTER TABLE unit_berichte_settings
    ADD COLUMN allow_foreign_unit_personnel TINYINT(1) NOT NULL DEFAULT 0 AFTER import_personnel_from_divera;
