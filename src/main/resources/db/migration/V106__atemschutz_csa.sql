ALTER TABLE unit_atemschutz_settings
    ADD COLUMN csa_course_id BIGINT NULL AFTER agt_course_id,
    ADD COLUMN csa_warn_days INT NOT NULL DEFAULT 90 AFTER uebung_warn_days,
    ADD COLUMN csa_notify_instructors TINYINT(1) NOT NULL DEFAULT 0 AFTER uebung_notify_instructors,
    ADD COLUMN csa_notify_carriers TINYINT(1) NOT NULL DEFAULT 0 AFTER uebung_notify_carriers,
    ADD COLUMN csa_cc_person_ids TEXT NULL AFTER uebung_cc_person_ids;

ALTER TABLE unit_atemschutz_settings
    ADD CONSTRAINT fk_unit_atemschutz_csa_course
    FOREIGN KEY (csa_course_id) REFERENCES courses (id) ON DELETE SET NULL;

UPDATE unit_atemschutz_settings
SET csa_warn_days = uebung_warn_days;

ALTER TABLE incident_report_personnel
    ADD COLUMN uses_csa BOOLEAN NOT NULL DEFAULT FALSE AFTER uses_pa;
