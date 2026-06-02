-- Testmodus: getrennte Testdaten von Produktivdaten

CREATE TABLE application_settings (
    id BIGINT NOT NULL,
    test_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO application_settings (id, test_mode_enabled) VALUES (1, FALSE);

ALTER TABLE units ADD COLUMN test_data BOOLEAN NOT NULL DEFAULT FALSE AFTER active;
ALTER TABLE qualification_types ADD COLUMN test_data BOOLEAN NOT NULL DEFAULT FALSE AFTER active;
ALTER TABLE courses ADD COLUMN test_data BOOLEAN NOT NULL DEFAULT FALSE AFTER active;
ALTER TABLE persons ADD COLUMN test_data BOOLEAN NOT NULL DEFAULT FALSE AFTER status;

CREATE INDEX idx_units_test_data ON units (test_data);
CREATE INDEX idx_persons_test_data ON persons (unit_id, test_data);
