# Feuerwehr-Manager (Java / Spring Boot)

Repository: [github.com/Boede89/feuerwehr-manager](https://github.com/Boede89/feuerwehr-manager)

## Installation mit einem Befehl (auf dem Server, nach `git clone`)

```bash
cd feuerwehr-manager
chmod +x install.sh && ./install.sh
```

Danach im Browser: **http://\<Server-IP\>:8080** – Einrichtung Divera über **Einstellungen** in der Web-UI (`docs/SCHRITT-FUER-SCHRITT.md`).

**Proxmox / wie viele Container / Debian vs. Ubuntu / RAM:** siehe **`docs/PROXMOX-RATGEBER.md`**.

**Debian 12 – alles vor `git clone` (Docker, nesting, Checkliste):** siehe **`docs/DEBIAN-VORBEREITUNG.md`**.

---

## Start mit Docker (manuell, gleiche Wirkung wie `install.sh`)

```bash
cd feuerwehr-manager
docker compose up --build -d
```
- Web: http://localhost:8080  
- MySQL (Host): Port **3309** (User `ff`, Passwort `ffsecret`, DB `feuerwehr_manager`)

**Schritt-für-Schritt (ein Docker-Befehl, Einrichtung in der Web-UI):** [docs/SCHRITT-FUER-SCHRITT.md](docs/SCHRITT-FUER-SCHRITT.md)

## Divera-Zugang (Web-UI)

1. Im Browser **Einstellungen** öffnen: `http://localhost:8080/settings/divera?unit=1` (oder Kachel **Einstellungen** im Dashboard).
2. **API-Basis-URL** und **Divera Access Key** eintragen und **Speichern**.
3. Dashboard prüfen; JSON: `GET http://localhost:8080/api/v1/units/1/divera/alarms`

Technische Details: [docs/DIVERA.md](docs/DIVERA.md)

## Optional: Polling (Platzhalter für später Push)

Ohne Dateien zu ändern: auf dem Host vor `docker compose up` die Variable setzen (siehe [docs/SCHRITT-FUER-SCHRITT.md](docs/SCHRITT-FUER-SCHRITT.md) Abschnitt 7).

Alternativ in `application.yml` den Wert `feuerwehr.divera.poll-enabled` setzen (nur wenn du Dateien anpassen möchtest).

## Lokal ohne Docker (nur wenn MySQL läuft)

```bash
mvn spring-boot:run
```

Vorher MySQL anlegen und Nutzer/DB wie in `docker-compose.yml` oder `application.yml` setzen.

## Anmeldung

- Formular-Login unter `/login` (Spring Security, BCrypt, CSRF).
- Erst-Admin beim ersten Start (Umgebungsvariable `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD`).
- Passwort min. 4 Zeichen (konfigurierbar); Audit-Log und sichere Kontolöschung im Hintergrund – siehe [docs/LOGIN.md](docs/LOGIN.md).
- **RFID:** Datenmodell und API vorbereitet (`POST /api/v1/auth/rfid`); Registrierung der Chips in der Verwaltung folgt.

Vor Produktion zusätzlich: HTTPS, `.env` aus `.env.example` (u. a. **`FEUERWEHR_TOTP_ENCRYPTION_KEY`** für verschlüsselte 2FA-Secrets — DSGVO-Pflicht), starkes Bootstrap-Passwort, `FEUERWEHR_AUDIT_SALT` setzen. Siehe [docs/DSGVO.md](docs/DSGVO.md).
