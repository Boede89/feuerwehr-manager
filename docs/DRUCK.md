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

Anmeldung: Benutzer **`print`**, Passwort aus `.env` (`CUPS_ADMIN_PASSWORD`).

**Wichtig:** Das Image `olbat/cupsd` hat intern immer den Benutzer `print`. Ohne unser Entrypoint-Skript ist das Passwort fest **`print`** — nicht der Wert aus der `.env`. Nach Update setzt `docker/cups/entrypoint.sh` das Passwort beim Start aus `CUPS_ADMIN_PASSWORD`.

Falls die Anmeldung trotzdem scheitert: gespeicherte Zugangsdaten im Browser löschen (oder Inkognito-Fenster) und erneut versuchen.

Falls der Drucker in CUPS existiert, aber der App-Container ihn nicht sieht: Warteschlange **freigeben**:

```bash
docker exec ffm_cups lpadmin -p Zentrale -o printer-is-shared=true
docker exec ffm_app lpstat -h ffm_cups:631 -U print:DEIN_PASSWORT -t
```

Ohne `printer-is-shared=true` meldet Remote-`lpstat` nur `scheduler is running`, listet aber keine Drucker.

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
| Druckauftrag „angehalten“, 0 k, Benutzer „Unbekannt“ | Remote-Druck: `cupsctl --remote-any`, Drucker freigeben, alte Jobs löschen (siehe unten) |

### Druckweg (Print-Relay)

Die App sendet PDFs an einen **Print-Relay** im CUPS-Container (`ffm_cups:8766`). Dort wird **lokal** gedruckt — wie der CUPS-Testdruck. Das vermeidet „angehaltene“ Remote-Jobs mit 0 k.

Standard in `docker-compose.yml`: `FEUERWEHR_PRINT_RELAY_URL=http://ffm_cups:8766`

### Angehaltene Druckaufträge (0 k) — bereinigen

```bash
docker exec ffm_cups cancel -a Zentrale
```

`cupsctl --remote-any` kann „Internal Server Error“ melden — für den Relay-Druck nicht nötig.
| Leere Seite / falscher Treiber | PostScript-Option nur bei Bedarf aktivieren |
