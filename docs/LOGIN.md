# Anmeldung & RFID

## Erster Start

1. **Nur wenn die Datenbank noch keinen Benutzer hat**, legt die App beim Start automatisch einen Administrator an (Ersteinrichtung).
2. Standard: Benutzername `admin`, Passwort `changeme` (nur Entwicklung).
3. In Produktion: `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` setzen **vor** dem ersten Start.
4. Browser: **https://&lt;Server&gt;** (über Caddy) → Weiterleitung zu `/login`.

Ein gelöschter Bootstrap-`admin` wird **nicht** bei jedem Neustart neu angelegt. Nur ein komplett leeres System (0 aktive Konten) löst die Erstanlage aus.

Details zu HTTPS: [HTTPS.md](HTTPS.md)

## Anmeldung schlägt fehl?

**Prüfen, ob der Admin existiert** (auf dem Server):

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager \
  -e "SELECT id, username, active, password_hash IS NOT NULL AS has_pw FROM users;"
```

- **Keine Zeile (leere DB):** App neu starten (`docker compose restart app`) – legt `admin` an.
- **Nur gelöschte Konten, aber andere Benutzer existieren:** kein neuer `admin` – anderen Superadmin nutzen oder DB zurücksetzen.
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
- **Weitere Benutzer (Admin):** Adminpanel → **Benutzer** – anlegen, Rolle, Passwort, RFID-Chip

## RFID-Chip

- Chips registrieren: Admin → Benutzer → **RFID-Chip** (UID z. B. aus `usbrdrtool -t` oder Seriell-Test)
- Login-Seite (HTTPS, Chrome/Brave): **Lesegerät verbinden** → Chip auflegen
- API: `POST /api/v1/auth/rfid` mit JSON `{ "cardUid": "…" }`
- Deaktivieren: `FEUERWEHR_RFID_API_ENABLED=false`

Lesegerät: YSoft USB Reader im **COM-/Serial-Modus** (nicht Keyboard-Wedge).
