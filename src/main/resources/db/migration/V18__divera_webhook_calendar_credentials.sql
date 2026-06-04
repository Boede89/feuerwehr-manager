-- Divera Webhook-Secret pro Einheit; Google Service Account für Kalender-Schreibrechte

ALTER TABLE unit_divera_settings
    ADD COLUMN webhook_secret VARCHAR(255) NULL AFTER access_key;

ALTER TABLE unit_calendar_settings
    ADD COLUMN service_account_json MEDIUMTEXT NULL AFTER calendar_id;
