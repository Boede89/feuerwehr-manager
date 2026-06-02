-- DSGVO: Einwilligung, Transparenz, Audit (siehe docs/DSGVO.md)

ALTER TABLE users
    ADD COLUMN privacy_notice_version VARCHAR(32) NULL AFTER active,
    ADD COLUMN privacy_notice_accepted_at TIMESTAMP NULL AFTER privacy_notice_version,
    ADD COLUMN last_login_at TIMESTAMP NULL AFTER privacy_notice_accepted_at,
    ADD COLUMN anonymized_at TIMESTAMP NULL AFTER last_login_at;

CREATE TABLE privacy_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    active_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_privacy_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE privacy_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notice_version VARCHAR(32) NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_hash VARCHAR(64) NULL,
    user_agent_hash VARCHAR(64) NULL,
    PRIMARY KEY (id),
    INDEX idx_consent_user (user_id),
    CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sicherheits-/Zugriffsprotokoll (keine Passwörter, keine Chip-IDs im Klartext)
CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NULL,
    subject_user_id BIGINT NULL,
    ip_hash VARCHAR(64) NULL,
    detail VARCHAR(512) NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_occurred (occurred_at),
    INDEX idx_audit_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO privacy_notices (version, title, content, active_from) VALUES (
    '1.0',
    'Datenschutzhinweis – Anmeldung',
    'Verantwortliche Stelle: [Name und Kontakt der Feuerwehr / des Trägers eintragen]\n\n'
    'Zweck: Anmeldung am Feuerwehr-Manager, Zugriffskontrolle und – sofern aktiviert – Anmeldung per RFID-Chip am dienstlichen Rechner.\n\n'
    'Verarbeitete Daten: Benutzername, Anzeigename, Passwort (nur als kryptographischer Hash), '
    'optional Chip-Kennung (technische ID, kein Name auf dem Chip), Zeitpunkt der Anmeldung, '
    'pseudonymisierte Protokolleinträge (IP nur als Hash).\n\n'
    'Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO (Nutzung des Systems) bzw. lit. f (IT-Sicherheit, Protokollierung).\n\n'
    'Speicherdauer: Kontodaten bis zur Löschung durch den Administrator; Protokolle gemäß Systemeinstellung (Standard 90 Tage).\n\n'
    'Ihre Rechte: Auskunft, Berichtigung, Löschung, Einschränkung, Widerspruch – wenden Sie sich an die oben genannte Stelle.\n\n'
    'Bitte passen Sie diesen Text in der Verwaltung an, bevor Sie den Betrieb produktiv starten.',
    CURRENT_TIMESTAMP
);
