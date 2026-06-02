-- Divera-RIC-Zuordnungen pro Person (Anzeige/Zuweisung im Personal-Modul)

CREATE TABLE person_divera_rics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    ric_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_person_ric_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    UNIQUE KEY uk_person_ric (person_id, ric_code),
    INDEX idx_person_ric_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
