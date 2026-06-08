-- Empfänger-Gruppe: ID optional (nur Bezeichnung möglich)

ALTER TABLE unit_divera_recipient_groups
    MODIFY COLUMN group_id VARCHAR(64) NULL;
