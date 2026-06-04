# DSGVO im Feuerwehr-Manager – Anmeldung & Benutzer

Die Software unterstützt eure **organisatorischen** Pflichten (Dokumentation, Verantwortlicher, AV-Verträge). Die **rechtliche Bewertung** obliegt der Feuerwehr / dem Träger.

## Anmeldung & Benutzerkonten

| Maßnahme | Umsetzung |
|----------|-----------|
| **Datenminimierung** | Benutzername, Anzeigename, Passwort-**Hash** (BCrypt), optional Chip-Kennung (technische ID) |
| **Zweckbindung** | Datenschutzhinweis Version 1.0 mit Zweck „Anmeldung / Zugriffskontrolle“ (anpassbar in DB-Tabelle `privacy_notices`) |
| **Einwilligung / Information** | Nach erstem Login: Bestätigung des aktuellen Hinweises (`privacy_consents`) |
| **Transparenz** | `/privacy/notice` öffentlich lesbar |
| **Sicherheit** | HTTPS in Produktion, CSRF, Session-Cookie `httpOnly`, Passwörter nie im Klartext |
| **2FA (TOTP)** | Geheimnisse in der DB **verschlüsselt** (AES-256-GCM), wenn `FEUERWEHR_TOTP_ENCRYPTION_KEY` gesetzt ist — in Produktion **Pflicht** |
| **Protokollierung** | `audit_events`: Login/Logout/Fehler **ohne** Passwort und **ohne** Chip-ID im Klartext; IP nur als SHA-256 mit Salt |
| **Speicherfrist Logs** | Standard **90 Tage**, danach automatische Löschung (`feuerwehr.dsgvo.audit-retention-days`) |
| **Löschung** | `UserService.anonymizeUser()` – Konto pseudonymisiert, RFID-Karten deaktiviert (Workflow in UI folgt) |

## RFID-Chip (vorbereitet)

- Chip-IDs sind **personenbezogen** (Zuordnung zu einem Konto).
- Speicherung nur als technische Kennung; Registrierung nur durch Administratoren (UI folgt).
- Anmeldung per API: `POST /api/v1/auth/rfid` – protokolliert als `RFID_LOGIN_*` ohne Chip-ID im Log-Detail.

## Konfiguration (Produktion)

```bash
cp .env.example .env
# In .env eintragen (nicht committen):
FEUERWEHR_TOTP_ENCRYPTION_KEY='…'              # Pflicht Produktion — z. B. openssl rand -base64 48
FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD='…'         # starkes Initialpasswort
FEUERWEHR_AUDIT_SALT='…'                       # zufällig, geheim halten
FEUERWEHR_AUDIT_RETENTION_DAYS=90

docker compose up -d --build
```

**Wichtig zu `FEUERWEHR_TOTP_ENCRYPTION_KEY`:** Einmal setzen und **nicht mehr ändern**, sobald Nutzer 2FA aktiviert haben. Ohne Key werden Secrets mit `plain:` gespeichert (nur für Entwicklung).

**HTTPS** vor dem Internet (Reverse Proxy). Session-Cookie `secure: true` hinter TLS (Spring Boot Konfiguration anpassen).

## Pflichten der Organisation

- Datenschutzhinweis in `privacy_notices` **anpassen** (Verantwortlicher, Kontakt).
- Verarbeitungsverzeichnis und ggf. AV-Vertrag für Hosting ergänzen.
- Betroffenenrechte (Auskunft/Löschung) organisatorisch + später in der App als Workflows.

Siehe auch Projektplan: `docs/MODUL-KONZEPT.md` Abschnitt 9 (übergeordnetes Repo).
