ALTER TABLE unit_atemschutz_settings
    ADD COLUMN g26_warn_days INT NOT NULL DEFAULT 90 AFTER warn_days,
    ADD COLUMN strecke_warn_days INT NOT NULL DEFAULT 90 AFTER g26_warn_days,
    ADD COLUMN uebung_warn_days INT NOT NULL DEFAULT 90 AFTER strecke_warn_days,
    ADD COLUMN g26_notify_instructors TINYINT(1) NOT NULL DEFAULT 0 AFTER uebung_warn_days,
    ADD COLUMN strecke_notify_instructors TINYINT(1) NOT NULL DEFAULT 0 AFTER g26_notify_instructors,
    ADD COLUMN uebung_notify_instructors TINYINT(1) NOT NULL DEFAULT 0 AFTER strecke_notify_instructors,
    ADD COLUMN g26_cc_user_ids TEXT NULL AFTER uebung_notify_instructors,
    ADD COLUMN strecke_cc_user_ids TEXT NULL AFTER g26_cc_user_ids,
    ADD COLUMN uebung_cc_user_ids TEXT NULL AFTER strecke_cc_user_ids,
    ADD COLUMN instructor_user_ids TEXT NULL AFTER uebung_cc_user_ids;

UPDATE unit_atemschutz_settings
SET g26_warn_days = warn_days,
    strecke_warn_days = warn_days,
    uebung_warn_days = warn_days;

UPDATE unit_atemschutz_settings
SET g26_cc_user_ids = cc_user_ids,
    strecke_cc_user_ids = cc_user_ids,
    uebung_cc_user_ids = cc_user_ids
WHERE cc_user_ids IS NOT NULL AND cc_user_ids <> '';
