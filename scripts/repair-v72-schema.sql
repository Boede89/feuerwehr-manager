-- Schema für Anwesenheitslisten (korrigierte V72 — FK auf unit_termine)

DROP TABLE IF EXISTS attendance_report_personnel;
DROP TABLE IF EXISTS attendance_reports;

CREATE TABLE attendance_reports (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id             BIGINT       NOT NULL,
    unit_termin_id      BIGINT       NULL,
    report_number       VARCHAR(32)  NULL,
    event_date          DATE         NOT NULL,
    start_time          TIME         NULL,
    end_time            TIME         NULL,
    title               VARCHAR(150) NOT NULL,
    termin_category     VARCHAR(32)  NULL,
    location            VARCHAR(300) NOT NULL DEFAULT '',
    notes               TEXT         NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ENTWURF',
    created_by_user_id  BIGINT       NULL,
    created_by_name     VARCHAR(255) NULL,
    released_by_user_id BIGINT       NULL,
    released_at         TIMESTAMP    NULL,
    test_data           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_report_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_report_termin FOREIGN KEY (unit_termin_id) REFERENCES unit_termine (id) ON DELETE SET NULL,
    CONSTRAINT fk_attendance_report_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_attendance_report_released_by FOREIGN KEY (released_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uk_attendance_report_termin UNIQUE (unit_termin_id),
    CONSTRAINT uk_attendance_report_number_unit UNIQUE (unit_id, report_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_attendance_reports_unit_date ON attendance_reports (unit_id, event_date DESC);
CREATE INDEX idx_attendance_reports_status ON attendance_reports (unit_id, status);

CREATE TABLE attendance_report_personnel (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    attendance_report_id  BIGINT       NOT NULL,
    person_id             BIGINT       NULL,
    display_name          VARCHAR(255) NOT NULL,
    attendance_status     VARCHAR(16)  NOT NULL DEFAULT 'PRESENT',
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_personnel_report FOREIGN KEY (attendance_report_id) REFERENCES attendance_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_personnel_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_attendance_personnel_report ON attendance_report_personnel (attendance_report_id, sort_order);
