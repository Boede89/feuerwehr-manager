-- Wartezeiten für automatischen Leitstellen-Abruf (Session-Ende)
ALTER TABLE unit_leitstellen_mail_settings
    ADD COLUMN depesche_wait_hours INT NOT NULL DEFAULT 6 AFTER abschluss_poll_interval_seconds,
    ADD COLUMN abschluss_wait_hours INT NOT NULL DEFAULT 24 AFTER depesche_wait_hours;
