CREATE TABLE strecke_termine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    termin_datum DATE NOT NULL,
    termin_zeit TIME NOT NULL DEFAULT '09:00:00',
    ort VARCHAR(255) NOT NULL DEFAULT '',
    max_teilnehmer INT NOT NULL DEFAULT 10,
    bemerkung TEXT,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_strecke_termin_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_strecke_termin_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_strecke_termine_unit_datum ON strecke_termine (unit_id, termin_datum);

CREATE TABLE strecke_zuordnungen (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    termin_id BIGINT NOT NULL,
    carrier_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'GEPLANT',
    benachrichtigt_am TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_strecke_zuordnung UNIQUE (termin_id, carrier_id),
    CONSTRAINT fk_strecke_zuordnung_termin FOREIGN KEY (termin_id) REFERENCES strecke_termine (id) ON DELETE CASCADE,
    CONSTRAINT fk_strecke_zuordnung_carrier FOREIGN KEY (carrier_id) REFERENCES atemschutz_carriers (id) ON DELETE CASCADE
);

CREATE INDEX idx_strecke_zuordnung_carrier ON strecke_zuordnungen (carrier_id);
