# Anmeldung & RFID (Vorbereitung)

## Erster Start

1. Nach Installation existiert **kein** Benutzer → beim Start wird automatisch ein Administrator angelegt.
2. Standard: Benutzername `admin`, Passwort `changeme` (nur Entwicklung).
3. In Produktion: `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` setzen **vor** dem ersten Start.
4. Browser: `http://<Server>:8080` → Weiterleitung zu `/login`.
5. Nach Login: **Datenschutzhinweis bestätigen** (einmalig pro Version).

## Passwort-Anmeldung

- Formular unter `/login`
- Sitzung läuft nach **8 Stunden** ab (konfigurierbar über `server.servlet.session.timeout`)

## RFID (später)

Technisch vorbereitet:

- Tabelle `user_rfid_cards` (Chip-ID pro Nutzer)
- `POST /api/v1/auth/rfid` mit JSON `{ "cardUid": "…" }`
- Deaktivieren: `FEUERWEHR_RFID_API_ENABLED=false`

**Lesegerät am PC:** Viele Geräte emulieren eine Tastatur; eine spätere UI kann die Eingabe abfangen und die API aufrufen. Chip-Registrierung erfolgt durch Admins (Verwaltungsoberfläche folgt).

## DSGVO

Siehe [DSGVO.md](DSGVO.md).
