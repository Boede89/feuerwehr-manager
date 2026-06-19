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
3. **Keine `.env`-Pflicht:** Standard-Login Web-UI ist **`print` / `print`**. Optional `CUPS_PRINTER_URI` in `.env` für automatische Warteschlange beim ersten Start.

### CUPS-Web-Oberfläche

**Nicht** der alte Printserver (`192.168.178.113`) — CUPS läuft auf dem **fw-manager-Host**:

```
http://<IP-des-fw-manager>:631
```

Anmeldung: Benutzer **`print`**, Passwort **`print`** (oder Wert aus `CUPS_ADMIN_PASSWORD`, falls in `.env` gesetzt).

**Wichtig:** `cupsd.conf` und `entrypoint.sh` werden per Bind-Mount aus dem Git-Repo geladen — nach `git pull` und `docker compose up -d` gilt die aktuelle Konfiguration, auch wenn das CUPS-Volume schon älter ist.

Falls die Anmeldung trotzdem scheitert: gespeicherte Zugangsdaten im Browser löschen (oder Inkognito-Fenster) und erneut versuchen. Danach Container neu starten:

```bash
docker compose up -d cups
```

Extremfall (altes Volume, Drucker neu anlegen): `docker compose down` und Volume `*_ffm_cups_data` löschen, dann `docker compose up -d`.

Falls der Drucker in CUPS existiert, aber der App-Container ihn nicht sieht: Warteschlange **freigeben**:

```bash
docker exec ffm_cups lpadmin -p Zentrale -o printer-is-shared=true
docker exec ffm_app lpstat -h cups:631 -U print:print -t
```

Ohne `printer-is-shared=true` meldet Remote-`lpstat` nur `scheduler is running`, listet aber keine Drucker.

## Beispiel: Konica Minolta bizhub im LAN

Drucker-IP aus der Drucker-Web-Oberfläche oder vom Router ablesen (z. B. `192.168.178.100`).

**Wichtig:** Hostnamen wie `BOEDES-C3350.localdomain` aus der Drucker-UI funktionieren im **CUPS-Docker-Container in der Regel nicht** — dort gibt es kein Windows-/mDNS-Namensauflösung. CUPS meldet dann „Ungültige Geräte-URI“. Stattdessen immer die **IP-Adresse** verwenden:

```
ipp://192.168.178.100/ipp/print
```

Falls das scheitert, **`ipps://`** probieren:

```
ipps://192.168.178.100/ipp/print
```

Keine Anführungszeichen um die URI eintragen.

### Drucker in der CUPS-Web-UI anlegen

1. `http://<IP-des-Servers>:631` → Anmeldung `print` / `print`
2. **Administration → Drucker hinzufügen**
3. Geräte-URI: `ipp://<Drucker-IP>/ipp/print` (nicht den Hostnamen aus der Drucker-UI kopieren)
4. Modell: **IPP Everywhere** (oder „everywhere“)

### Drucker per Skript (empfohlen)

Drucker-IP aus der Drucker-Web-UI (Netzwerkseite), **nicht** den Hostnamen (`BOEDES-C3350.localdomain`):

```bash
cd /opt/feuerwehr/feuerwehr-manager
git pull
chmod +x scripts/cups-add-printer.sh
./scripts/cups-add-printer.sh 192.168.10.50
docker compose restart cups app
```

In der App: **Admin → Einheit → Schnittstellen → Druckerverwaltung → Drucker laden**.

Manuell im Container:

```bash
lpadmin -p Zentrale -E -v ipp://192.168.178.100/ipp/print -m everywhere
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

## Zwei Server / Container — warum unterschiedlich?

**Git-Stand und Docker-Images können identisch sein** — die **CUPS-Drucker-Konfiguration nicht**:
Sie liegt im Volume `ffm_cups_data` **pro Server getrennt**. Ein Drucker, den du auf Container A in der CUPS-Web-UI anlegst, existiert auf Container B nicht.

| Server A (funktioniert) | Server B (leer) |
|-------------------------|-----------------|
| `lpstat -p` zeigt `Zentrale` | `lpstat -p` zeigt nichts |
| App listet Drucker | App: „scheduler is running“ |

**Diagnose auf dem betroffenen Server:**

```bash
cd /opt/feuerwehr/feuerwehr-manager   # oder /opt/feuerwehr/feuerwehr-manager
git pull
chmod +x scripts/cups-diagnose.sh
./scripts/cups-diagnose.sh
```

Wenn `lpstat -p` leer ist, ist der Drucker **auf diesem Server** nicht angelegt (Web-UI-Anlage evtl. fehlgeschlagen oder falscher Hostname in der URI).

**Nach git pull** unbedingt:

```bash
docker compose up -d --build app
docker compose restart cups
```

(Für Print-Relay `/printers` und Druckerliste aus dem CUPS-Container.)

## Fehlerbehebung

| Symptom | Prüfen |
|---------|--------|
| App „Restarting“, Log: Flyway V37 | `./scripts/repair-flyway-v37.sh` (siehe docs/FLYWAY-REPARATUR.md) |
| „Ungültige Geräte-URI“ beim Drucker anlegen | Hostname (`.localdomain`, `.local`) durch **IP-Adresse** ersetzen; keine Anführungszeichen; ggf. `ipps://` statt `ipp://` |
| Web-UI / App nicht erreichbar | `docker compose ps`; Ports **80**, **443**, **8080**; nach Update `docker compose up -d` und ggf. `docker compose restart caddy app` |
| Nur `:8080` tot, HTTPS geht | App-Logs: `docker compose logs app --tail 80` (MySQL/CUPS-Start abwarten) |
| „lp-Befehl nicht gefunden“ | App-Container neu bauen (`--build`) |
| „Keine Drucker gefunden“ / `scheduler is running` | In CUPS ist **noch kein Drucker** angelegt — `./scripts/cups-add-printer.sh <IP>` |
| Druckauftrag „angehalten“, 0 k, Benutzer „Unbekannt“ | Remote-Druck: `cupsctl --remote-any`, Drucker freigeben, alte Jobs löschen (siehe unten) |

### Druckweg (Print-Relay)

Die App sendet PDFs an einen **Print-Relay** im CUPS-Container (`http://cups:8766` — Docker-Service-Name, **nicht** `ffm_cups`, wegen Java-URI). Dort wird **lokal** gedruckt — wie der CUPS-Testdruck.

Standard in `docker-compose.yml`: `FEUERWEHR_PRINT_RELAY_URL=http://cups:8766`

### Angehaltene Druckaufträge (0 k) — bereinigen

```bash
docker exec ffm_cups cancel -a Zentrale
```

`cupsctl --remote-any` kann „Internal Server Error“ melden — für den Relay-Druck nicht nötig.
| Leere Seite / falscher Treiber | PostScript-Option nur bei Bedarf aktivieren |
