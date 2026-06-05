-- Personal-Modul (FW-Hub Parität): Stammdaten-Erweiterung + Qualifikationen, Ausrüstung, Ehrungen, Anwesenheit

ALTER TABLE persons
    ADD COLUMN personnel_number VARCHAR(50) NULL AFTER notes,
    ADD COLUMN entry_date DATE NULL AFTER personnel_number,
    ADD COLUMN exit_date DATE NULL AFTER entry_date,
    ADD COLUMN profile_updated_by_id BIGINT NULL AFTER exit_date,
    ADD COLUMN profile_updated_by_name VARCHAR(255) NULL AFTER profile_updated_by_id;

ALTER TABLE persons
    ADD CONSTRAINT fk_person_profile_updated_by
        FOREIGN KEY (profile_updated_by_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE person_qualifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    acquired_at DATE NULL,
    expires_at DATE NULL,
    notes VARCHAR(200) NULL,
    is_health_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pq_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_person_qualifications_person ON person_qualifications(person_id);

CREATE TABLE person_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    identifier VARCHAR(100) NULL,
    issued_at DATE NULL,
    expires_at DATE NULL,
    notes VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ausgegeben',
    returned_at DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pe_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_person_equipment_person ON person_equipment(person_id);

CREATE TABLE person_honors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    awarded_at DATE NULL,
    notes VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'aktiv',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ph_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_person_honors_person ON person_honors(person_id);

CREATE TABLE person_attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'present',
    notes TEXT NULL,
    created_by_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pa_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_created_by FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_person_attendance_person ON person_attendance(person_id);
CREATE INDEX idx_person_attendance_date ON person_attendance(service_date);

-- Termine (einheitsbezogen)
CREATE TABLE termin_typen (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT '#6b7280',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tt_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);

CREATE TABLE unit_termine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    typ_id BIGINT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NULL,
    location VARCHAR(150) NULL,
    description VARCHAR(500) NULL,
    created_by_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ut_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT fk_ut_typ FOREIGN KEY (typ_id) REFERENCES termin_typen(id) ON DELETE SET NULL,
    CONSTRAINT fk_ut_created_by FOREIGN KEY (created_by_id) REFERENCES users(id)
);

CREATE TABLE termin_assignments (
    termin_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (termin_id, person_id),
    CONSTRAINT fk_ta_termin FOREIGN KEY (termin_id) REFERENCES unit_termine(id) ON DELETE CASCADE,
    CONSTRAINT fk_ta_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

-- Zeiterfassung
ALTER TABLE users ADD COLUMN badge_code VARCHAR(128) NULL;
CREATE UNIQUE INDEX idx_users_badge_code ON users(badge_code);

CREATE TABLE time_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    check_in TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_out TIMESTAMP NULL,
    typ VARCHAR(50) NOT NULL DEFAULT 'wachdienst',
    notes TEXT NULL,
    termin_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_te_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_te_termin FOREIGN KEY (termin_id) REFERENCES unit_termine(id) ON DELETE SET NULL
);

CREATE INDEX idx_time_entries_person ON time_entries(person_id);
CREATE INDEX idx_time_entries_checkin ON time_entries(check_in);

ALTER TABLE application_settings
    ADD COLUMN qualification_warn_days INT NOT NULL DEFAULT 90 AFTER privacy_hoster;
