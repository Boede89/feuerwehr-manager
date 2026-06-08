ALTER TABLE unit_atemschutz_settings
    CHANGE COLUMN g26_cc_user_ids g26_cc_person_ids TEXT NULL,
    CHANGE COLUMN strecke_cc_user_ids strecke_cc_person_ids TEXT NULL,
    CHANGE COLUMN uebung_cc_user_ids uebung_cc_person_ids TEXT NULL;
