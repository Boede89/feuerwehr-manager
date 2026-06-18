# Feuerwehr-Manager (Java / Spring Boot)

Repository: [github.com/Boede89/feuerwehr-manager](https://github.com/Boede89/feuerwehr-manager)

## Installation mit einem Befehl (frischer Server / LXC)

```bash
curl -fsSL https://raw.githubusercontent.com/Boede89/feuerwehr-manager/main/scripts/install-server.sh | bash
```

Installiert Docker (falls nötig), klont nach `/opt/feuerwehr-manager`, erzeugt `.env` und startet die App.

**Leere DB** (vor SQL-Import in der Web-UI): `… | bash -s -- --fresh`

**Bereits geklont:** `sudo ./scripts/install-server.sh` oder `sudo ./install.sh`

Danach im Browser: **http://&lt;Server-IP&gt;:8080** — Details: `docs/SCHRITT-FUER-SCHRITT.md`, `docs/HTTPS.md`

**Proxmox / wie viele Container / Debian vs. Ubuntu / RAM:** siehe **`docs/PROXMOX-RATGEBER.md`**.

**Debian 12 – alles vor `git clone` (Docker, nesting, Checkliste):** siehe **`docs/DEBIAN-VORBEREITUNG.md`**.

---

## Start mit Docker (manuell, gleiche Wirkung wie `install.sh`)

```bash
cd feuerwehr-manager
docker compose up --build -d
```
- Web: https://fw-manager.home.arpa (Caddy) oder http://localhost:8080 (Debug)  
- MySQL (Host): Port **3309** (User `ff`, Passwort `ffsecret`, DB `feuerwehr_manager`)

**Schritt-für-Schritt (ein Docker-Befehl, Einrichtung in der Web-UI):** [docs/SCHRITT-FUER-SCHRITT.md](docs/SCHRITT-FUER-SCHRITT.md)

## Divera-Zugang (Web-UI)

1. Im Browser **Einstellungen** öffnen: `https://fw-manager.home.arpa/settings/divera?unit=1` (oder Kachel **Einstellungen** im Dashboard).
2. **API-Basis-URL** und **Divera Access Key** eintragen und **Speichern**.
3. Dashboard prüfen; JSON: `GET https://fw-manager.home.arpa/api/v1/units/1/divera/alarms`

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
- **RFID:** Login per Chip über HTTPS + Web Serial (Brave/Chrome); Chips im Adminpanel registrieren (`docs/LOGIN.md`, `docs/HTTPS.md`).

Vor Produktion zusätzlich: HTTPS, `.env` aus `.env.example` (u. a. **`FEUERWEHR_TOTP_ENCRYPTION_KEY`** für verschlüsselte 2FA-Secrets — DSGVO-Pflicht), starkes Bootstrap-Passwort, `FEUERWEHR_AUDIT_SALT` setzen. Siehe [docs/DSGVO.md](docs/DSGVO.md).
