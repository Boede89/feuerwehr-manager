ALTER TABLE unit_berichte_settings
    ADD COLUMN einsatz_release_create_geraetewart BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN einsatz_release_print_report BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN einsatz_release_print_geraetewart BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN anwesenheit_release_print_report BOOLEAN NOT NULL DEFAULT FALSE;
