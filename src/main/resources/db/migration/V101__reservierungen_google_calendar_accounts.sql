ALTER TABLE unit_reservierungen_settings
    ADD COLUMN vehicle_google_calendar_account_ids_json TEXT NULL AFTER vehicle_google_calendar_enabled,
    ADD COLUMN room_google_calendar_account_ids_json TEXT NULL AFTER room_google_calendar_enabled;

ALTER TABLE reservation_calendar_events
    ADD COLUMN calendar_account_id BIGINT NULL AFTER reservation_id;

ALTER TABLE reservation_calendar_events
    DROP INDEX uk_res_cal_link;

ALTER TABLE reservation_calendar_events
    ADD UNIQUE KEY uk_res_cal_link (reservation_kind, reservation_id, calendar_account_id);

ALTER TABLE reservation_calendar_events
    ADD CONSTRAINT fk_res_cal_account
        FOREIGN KEY (calendar_account_id) REFERENCES unit_calendar_accounts(id) ON DELETE SET NULL;
