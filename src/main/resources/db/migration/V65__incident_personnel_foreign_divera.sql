ALTER TABLE incident_report_personnel
    ADD COLUMN divera_ucr_id VARCHAR(32) NULL AFTER source,
    ADD COLUMN foreign_unit_id BIGINT NULL AFTER divera_ucr_id;

ALTER TABLE incident_report_personnel
    ADD CONSTRAINT fk_incident_report_personnel_foreign_unit
        FOREIGN KEY (foreign_unit_id) REFERENCES units (id);
