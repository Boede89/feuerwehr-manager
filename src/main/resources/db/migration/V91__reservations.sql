-- Fahrzeug- und Raumreservierungen (angelehnt an feuerwehr-app)

CREATE TABLE vehicle_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    requester_user_id BIGINT NULL,
    requester_name VARCHAR(255) NOT NULL,
    requester_email VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    location VARCHAR(255) NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT NULL,
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    divera_event_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_vehicle_res_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_res_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_res_requester FOREIGN KEY (requester_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_vehicle_res_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_vehicle_res_unit_status (unit_id, status),
    INDEX idx_vehicle_res_vehicle_time (vehicle_id, start_at, end_at)
);

CREATE TABLE room_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    requester_user_id BIGINT NULL,
    requester_name VARCHAR(255) NOT NULL,
    requester_email VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    location VARCHAR(255) NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT NULL,
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME(6) NULL,
    divera_event_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_room_res_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_res_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_room_res_requester FOREIGN KEY (requester_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_room_res_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_room_res_unit_status (unit_id, status),
    INDEX idx_room_res_room_time (room_id, start_at, end_at)
);

CREATE TABLE reservation_calendar_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    reservation_kind VARCHAR(16) NOT NULL,
    reservation_id BIGINT NOT NULL,
    google_event_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_res_cal_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    UNIQUE KEY uk_res_cal_link (reservation_kind, reservation_id),
    INDEX idx_res_cal_google (google_event_id)
);

CREATE TABLE unit_reservierungen_settings (
    unit_id BIGINT PRIMARY KEY,
    vehicle_sort_mode VARCHAR(16) NOT NULL DEFAULT 'manual',
    vehicle_divera_enabled TINYINT(1) NOT NULL DEFAULT 0,
    vehicle_google_calendar_enabled TINYINT(1) NOT NULL DEFAULT 0,
    vehicle_divera_default_group_id VARCHAR(32) NULL,
    vehicle_divera_groups_json TEXT NULL,
    vehicle_loesch_warn_enabled TINYINT(1) NOT NULL DEFAULT 0,
    vehicle_loesch_min_available INT NOT NULL DEFAULT 1,
    vehicle_loesch_vehicle_ids_json TEXT NULL,
    vehicle_notification_user_ids_json TEXT NULL,
    room_sort_mode VARCHAR(16) NOT NULL DEFAULT 'manual',
    room_divera_enabled TINYINT(1) NOT NULL DEFAULT 0,
    room_google_calendar_enabled TINYINT(1) NOT NULL DEFAULT 0,
    room_divera_default_group_id VARCHAR(32) NULL,
    room_notification_user_ids_json TEXT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_res_settings_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);
