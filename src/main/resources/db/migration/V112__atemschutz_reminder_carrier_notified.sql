ALTER TABLE atemschutz_reminder_log
    ADD COLUMN carrier_notified TINYINT(1) NOT NULL DEFAULT 0 AFTER sent_at;
