CREATE TABLE unit_uvv_settings (
    unit_id BIGINT NOT NULL PRIMARY KEY,
    interval_months INT NOT NULL DEFAULT 12,
    warn_days INT NOT NULL DEFAULT 30,
    notify_person TINYINT(1) NOT NULL DEFAULT 0,
    notification_user_ids TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_uvv_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE TABLE uvv_campaigns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    content_text MEDIUMTEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    attendance_report_id BIGINT,
    unit_termin_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_uvv_campaign_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_uvv_campaign_attendance FOREIGN KEY (attendance_report_id) REFERENCES attendance_reports (id) ON DELETE SET NULL,
    CONSTRAINT fk_uvv_campaign_termin FOREIGN KEY (unit_termin_id) REFERENCES unit_termine (id) ON DELETE SET NULL
);

CREATE INDEX idx_uvv_campaigns_unit ON uvv_campaigns (unit_id);
CREATE INDEX idx_uvv_campaigns_status ON uvv_campaigns (unit_id, status);

CREATE TABLE uvv_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    question_text VARCHAR(1024) NOT NULL,
    option_a VARCHAR(512) NOT NULL,
    option_b VARCHAR(512) NOT NULL,
    option_c VARCHAR(512),
    option_d VARCHAR(512),
    correct_option CHAR(1) NOT NULL,
    CONSTRAINT fk_uvv_question_campaign FOREIGN KEY (campaign_id) REFERENCES uvv_campaigns (id) ON DELETE CASCADE
);

CREATE INDEX idx_uvv_questions_campaign ON uvv_questions (campaign_id);

CREATE TABLE uvv_completions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    completed_on DATE NOT NULL,
    channel VARCHAR(16) NOT NULL,
    recorded_by_user_id BIGINT,
    recorded_by_display_name VARCHAR(255),
    quiz_passed TINYINT(1) NOT NULL DEFAULT 0,
    notes VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_uvv_completion_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT fk_uvv_completion_campaign FOREIGN KEY (campaign_id) REFERENCES uvv_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_uvv_completion_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_uvv_completion_person_campaign UNIQUE (person_id, campaign_id)
);

CREATE INDEX idx_uvv_completions_person ON uvv_completions (person_id);
CREATE INDEX idx_uvv_completions_completed ON uvv_completions (completed_on);

CREATE TABLE uvv_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    mail_kind VARCHAR(16) NOT NULL,
    next_due_on DATE NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    person_notified TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_uvv_reminder_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT uq_uvv_reminder UNIQUE (person_id, mail_kind, next_due_on)
);
