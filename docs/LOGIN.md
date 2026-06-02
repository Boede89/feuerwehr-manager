# Anmeldung & RFID (Vorbereitung)

## Erster Start

1. Nach Installation existiert **kein** Benutzer → beim Start wird automatisch ein Administrator angelegt.
2. Standard: Benutzername `admin`, Passwort `changeme` (nur Entwicklung).
3. In Produktion: `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` setzen **vor** dem ersten Start.
4. Browser: `http://<Server>:8080` → Weiterleitung zu `/login`.
## Anmeldung schlägt fehl?

**Prüfen, ob der Admin existiert** (auf dem Server):

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager \
  -e "SELECT id, username, active, password_hash IS NOT NULL AS has_pw FROM users;"
```

- **Keine Zeile:** App neu starten (`docker compose restart app`) – legt `admin` an.
- **Zeile vorhanden, Login geht nicht:** Passwort einmalig zurücksetzen:

```bash
export FEUERWEHR_BOOTSTRAP_ADMIN_RESET_PASSWORD=true
export FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD=changeme
docker compose up -d
unset FEUERWEHR_BOOTSTRAP_ADMIN_RESET_PASSWORD
```

Danach mit `admin` / `changeme` anmelden und `RESET` wieder **aus** lassen.

## Passwort-Anmeldung

- Formular unter `/login`
- Sitzung läuft nach **8 Stunden** ab (konfigurierbar über `server.servlet.session.timeout`)
- **Eigenes Passwort ändern:** Einstellungen → **Mein Passwort** (`/profile/password`)
- **Weitere Benutzer (Admin):** Einstellungen → **Benutzer** (`/settings/users`) – anlegen, Rolle, Passwort, RFID-Chip

## RFID (später)

Technisch vorbereitet:

- Tabelle `user_rfid_cards` (Chip-ID pro Nutzer)
- `POST /api/v1/auth/rfid` mit JSON `{ "cardUid": "…" }`
- Deaktivieren: `FEUERWEHR_RFID_API_ENABLED=false`

**Lesegerät am PC:** Viele Geräte emulieren eine Tastatur; eine spätere UI kann die Eingabe abfangen und die API aufrufen. Chip-Registrierung erfolgt durch Admins (Verwaltungsoberfläche folgt).
