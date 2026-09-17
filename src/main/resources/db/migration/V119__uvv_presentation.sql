ALTER TABLE uvv_campaigns
    ADD COLUMN presentation_original_name VARCHAR(255) NULL,
    ADD COLUMN presentation_stored_name VARCHAR(255) NULL,
    ADD COLUMN presentation_mime_type VARCHAR(128) NULL,
    ADD COLUMN presentation_page_count INT NULL;
