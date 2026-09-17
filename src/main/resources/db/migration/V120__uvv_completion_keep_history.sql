-- Historie behalten, wenn eine Kampagne gelöscht wird:
-- Titel/Datum werden auf der Erledigung gespeichert, campaign_id wird nullable (ON DELETE SET NULL).

ALTER TABLE uvv_completions
    ADD COLUMN campaign_title VARCHAR(255) NULL,
    ADD COLUMN campaign_event_date DATE NULL;

UPDATE uvv_completions c
    INNER JOIN uvv_campaigns camp ON camp.id = c.campaign_id
SET c.campaign_title = camp.title,
    c.campaign_event_date = camp.event_date
WHERE c.campaign_title IS NULL;

UPDATE uvv_completions
SET campaign_title = 'UVV / Belehrung Kraftfahrer'
WHERE campaign_title IS NULL OR campaign_title = '';

ALTER TABLE uvv_completions
    MODIFY COLUMN campaign_title VARCHAR(255) NOT NULL;

ALTER TABLE uvv_completions
    DROP FOREIGN KEY fk_uvv_completion_campaign;

ALTER TABLE uvv_completions
    MODIFY COLUMN campaign_id BIGINT NULL;

ALTER TABLE uvv_completions
    ADD CONSTRAINT fk_uvv_completion_campaign
        FOREIGN KEY (campaign_id) REFERENCES uvv_campaigns (id) ON DELETE SET NULL;
