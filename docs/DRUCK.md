# Druck (CUPS)

Direktdruck aus dem Feuerwehr-Manager über CUPS (`lp`), analog zur früheren Feuerwehr-App.

## Einstellungen in der App

**Admin → Einheit → Schnittstellen → Druckerverwaltung**

| Modus | Verhalten |
|--------|-----------|
| **Druckdialog** | PDF im Browser öffnen und dort drucken (Standard) |
| **CUPS-Drucker** | Server sendet PDF per `lp` an eine CUPS-Warteschlange |

Pro Einheit konfigurierbar:

- CUPS-Druckername (z. B. `Zentrale`)
- optional CUPS-Server (`user:passwort@host:631`)
- optional PostScript-Konvertierung (nur für ältere Drucker)

**Testdruck** und **Drucker laden** prüfen die Verbindung zu CUPS.

## Voraussetzungen

1. **CUPS-Client** im App-Container (`cups-client`, `ghostscript`, `poppler-utils`) — ab Image-Neubau via `docker compose up -d --build`.
2. **CUPS-Server** als Docker-Service `cups` (Port **631** auf dem Host des Managers).
3. In `.env`: `CUPS_SERVER=print:print@cups:631` und optional `CUPS_PRINTER_URI` für automatische Warteschlange.

### CUPS-Web-Oberfläche

**Nicht** der alte Printserver (`192.168.178.113`) — CUPS läuft auf dem **fw-manager-Host**:

```
http://<IP-des-fw-manager>:631
```

Anmeldung: Benutzer `print`, Passwort aus `.env` (`CUPS_ADMIN_PASSWORD`, Standard: `print`).

Nach `docker compose up -d` legt `cups-bootstrap` optional den Drucker aus `CUPS_PRINTER_NAME` / `CUPS_PRINTER_URI` an.

## Beispiel: Konica Minolta bizhub im LAN

Drucker: `192.168.178.100`, IPPS: `ipps://192.168.178.100/ipp/print`

Auf dem **gleichen Host** wie der Manager (oder in einem CUPS-Container) eine Warteschlange anlegen, z. B. `Zentrale`:

```bash
lpadmin -p Zentrale -E -v ipps://192.168.178.100/ipp/print -m everywhere
cupsenable Zentrale
cupsaccept Zentrale
```

In den Einheitseinstellungen: Modus **CUPS**, Drucker **Zentrale**, CUPS-Server leer (lokal) oder `host:631` / `user:pass@host:631`.

## API (Admin)

| Endpunkt | Beschreibung |
|----------|--------------|
| `POST /admin/unit/print` | Einstellungen speichern |
| `GET /admin/rest/unit/print/printers?unit=` | CUPS-Druckerliste |
| `POST /admin/rest/unit/print/test` | Testseite drucken |

## Fehlerbehebung

| Symptom | Prüfen |
|---------|--------|
| „lp-Befehl nicht gefunden“ | App-Container neu bauen (`--build`) |
| „Keine Drucker gefunden“ | CUPS-Server, `lpstat -t`, Firewall Port 631 |
| „Unauthorized“ | `CUPS_SERVER` mit korrektem Benutzer (olbat/cupsd: oft `print`/`print`) |
| Leere Seite / falscher Treiber | PostScript-Option nur bei Bedarf aktivieren |
