ALTER TABLE unit_atemschutz_settings
    ADD COLUMN g26_notify_carriers TINYINT(1) NOT NULL DEFAULT 0 AFTER uebung_notify_instructors,
    ADD COLUMN strecke_notify_carriers TINYINT(1) NOT NULL DEFAULT 0 AFTER g26_notify_carriers,
    ADD COLUMN uebung_notify_carriers TINYINT(1) NOT NULL DEFAULT 0 AFTER strecke_notify_carriers;

CREATE TABLE atemschutz_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    carrier_id BIGINT NOT NULL,
    fitness_type VARCHAR(32) NOT NULL,
    mail_kind VARCHAR(16) NOT NULL,
    valid_until DATE NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_atemschutz_reminder_carrier FOREIGN KEY (carrier_id) REFERENCES atemschutz_carriers (id) ON DELETE CASCADE,
    CONSTRAINT uq_atemschutz_reminder UNIQUE (carrier_id, fitness_type, mail_kind, valid_until)
);
