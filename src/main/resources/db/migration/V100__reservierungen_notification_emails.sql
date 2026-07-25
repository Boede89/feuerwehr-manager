ALTER TABLE unit_reservierungen_settings
    ADD COLUMN vehicle_notification_emails_json TEXT NULL AFTER vehicle_notification_user_ids_json,
    ADD COLUMN room_notification_emails_json TEXT NULL AFTER room_notification_user_ids_json;
