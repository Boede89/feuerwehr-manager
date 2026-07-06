-- Geplante vs. gestartete manuelle Einsätze, Übung, Sondersignal

ALTER TABLE manual_alarms
    ADD COLUMN started BOOLEAN NOT NULL DEFAULT FALSE AFTER closed,
    ADD COLUMN started_at TIMESTAMP NULL AFTER started,
    ADD COLUMN exercise BOOLEAN NOT NULL DEFAULT FALSE AFTER started_at,
    ADD COLUMN sondersignal BOOLEAN NOT NULL DEFAULT TRUE AFTER exercise;

UPDATE manual_alarms SET started = TRUE WHERE started = FALSE;
