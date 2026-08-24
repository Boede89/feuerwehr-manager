ALTER TABLE unit_calendar_accounts
    ADD COLUMN delegated_user_email VARCHAR(254) NULL AFTER service_account_json;
