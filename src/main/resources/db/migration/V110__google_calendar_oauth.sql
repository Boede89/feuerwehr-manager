ALTER TABLE application_settings
    ADD COLUMN google_oauth_client_id VARCHAR(512) NULL,
    ADD COLUMN google_oauth_client_secret VARCHAR(512) NULL;

ALTER TABLE unit_calendar_accounts
    ADD COLUMN google_oauth_refresh_token TEXT NULL,
    ADD COLUMN google_oauth_user_email VARCHAR(254) NULL;
