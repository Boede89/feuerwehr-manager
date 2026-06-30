# Schritt-für-Schritt: Feuerwehr-Manager starten und einrichten

Repository: **https://github.com/Boede89/feuerwehr-manager**

Ziel: **Keine Projektdateien von Hand bearbeiten.** Du klonst das Repo, führst **einen Installationsbefehl** aus und trägst **Divera** in der **Web-Oberfläche** ein.

---

## Schritt 0: Code auf den Server holen

### Ein Befehl (frischer LXC, empfohlen)

Als **root** — **eine Zeile** kopieren und einfügen:

```bash
apt-get update && apt-get install -y git ca-certificates && git clone --depth 1 https://github.com/Boede89/feuerwehr-manager.git /opt/feuerwehr-manager && exec bash /opt/feuerwehr-manager/neuer-container --fresh
```

Das Skript installiert danach automatisch **curl, Docker**, legt `.env` an und startet die App.  
`--fresh` = leere Datenbank (für SQL-Import aus der Web-UI).

### Variante B — Manuell klonen

Auf dem Linux-Server (oder im Proxmox-LXC):

```bash
git clone https://github.com/Boede89/feuerwehr-manager.git
cd feuerwehr-manager
sudo ./neuer-container --fresh
```

---

## Voraussetzungen

- Auf dem Rechner/Container sind **Docker** und **Docker Compose** (Plugin `docker compose`) installiert und lauffähig.
- **Debian 12:** Schritt-für-Schritt **vor** dem Klonen → **[DEBIAN-VORBEREITUNG.md](DEBIAN-VORBEREITUNG.md)** (Proxmox LXC, nesting, Docker-Repo, `hello-world`-Test).
- Du hast den Ordner **`feuerwehr-manager`** auf den Server gebracht (per `git clone`) – darin liegen u. a. `docker-compose.yml` und `Dockerfile`.

---

## Schritt 1: In den Projektordner wechseln

```bash
cd feuerwehr-manager
```

*(Bereits erledigt, wenn du Schritt 0 ausgeführt hast.)*

---

## Schritt 2: Produktions-Secrets (DSGVO — vor dem ersten Start mit 2FA)

Für den **Produktions-Server** (nicht nur lokal testen):

```bash
cp .env.example .env
nano .env   # oder anderer Editor
```

Mindestens setzen:

| Variable | Zweck |
|----------|--------|
| `FEUERWEHR_TOTP_ENCRYPTION_KEY` | **Pflicht:** 2FA-Geheimnisse verschlüsselt in der DB (z. B. `openssl rand -base64 48`) |
| `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` | Starkes Admin-Passwort statt `changeme` |
| `FEUERWEHR_AUDIT_SALT` | Zufälliger Wert für gehashte IPs in Audit-Logs |

Die Datei `.env` **nicht** ins Git legen. Docker Compose liest sie automatisch.

**Hinweis:** Den TOTP-Key **nicht** nachträglich ändern, wenn schon Nutzer 2FA eingerichtet haben.

---

## Schritt 3: Installation (ein Befehl)

Wenn Sie **Variante A** (curl) oder `scripts/install-server.sh` nutzten, ist dieser Schritt **bereits erledigt**.

Sonst im Repository-Root:

```bash
chmod +x install.sh && sudo ./install.sh
```

Mit leerer Datenbank (vor SQL-Import):

```bash
sudo ./install.sh --fresh
```

---

## Schritt 4 (optional): Prüfen, ob alles läuft

```bash
docker compose ps
```

Beide Dienste sollten **running** bzw. MySQL **healthy** anzeigen.

Logs der App ansehen (optional):

```bash
docker compose logs -f app
```

Beenden der Log-Ansicht: `Strg+C`.

---

## Schritt 5: Web-Oberfläche öffnen

Im Browser (vom gleichen Netz aus, Firewall-Ports **80** und **443** freigeben):

- **Empfohlen:** `https://fw-manager.home.arpa` (Caddy, ggf. Zertifikatswarnung beim ersten Mal bestätigen)
- **Lokal am Server:** `https://fw-manager.home.arpa` oder `https://localhost`
- **Debug (ohne TLS):** `http://<IP-des-Servers>:8080`

Details: [HTTPS.md](HTTPS.md).

Du wirst zur **Anmeldung** weitergeleitet (`/login`).

**Erster Login (nur wenn noch kein Benutzer existiert):**

| Feld | Standard (Entwicklung) |
|------|-------------------------|
| Benutzername | `admin` |
| Passwort | `changeme` (in Produktion vor dem ersten Start `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` setzen) |

Danach erscheint das **Dashboard** (Layout Variante D) und die **Divera**-Liste.

Details: [LOGIN.md](LOGIN.md).

---

## Schritt 6: Passwort und Benutzer (empfohlen)

1. **Einstellungen** → **Mein Passwort** – `changeme` durch ein sicheres Passwort ersetzen.
2. Als **Administrator**: **Einstellungen** → **Benutzer** – weitere Konten für Kollegen anlegen.
3. Optional: RFID-Chip pro Benutzer unter **Bearbeiten** registrieren (für späteres Lesegerät).

---

## Schritt 6: Divera in der Web-UI eintragen (keine Datei-Änderung)

1. Im Dashboard auf die Kachel **„Einstellungen“** tippen/klicken  
   **oder** direkt aufrufen:  
   `https://fw-manager.home.arpa/settings/divera?unit=1`
2. **API-Basis-URL** prüfen: in der Regel **`https://app.divera247.com`** (sofern Divera nichts anderes vorgibt).
3. **Divera Access Key** eintragen (aus Divera 24/7 für die jeweilige Einheit).
4. **„Speichern“** drücken.

Hinweise auf der Seite:

- Ist bereits ein Key gespeichert, bleibt er erhalten, wenn du das Passwortfeld **leer** lässt und nur die URL änderst.
- Zum **Ändern** des Keys einfach einen **neuen** Wert eintragen und speichern.

---

## Schritt 6b: Testmodus + Webhook (24/7 in Proxmox)

Wenn die Anwendung **dauerhaft läuft** (Docker/Proxmox) und der **Testmodus** aktiv ist:

1. **Webhook in DIVERA** auf die URL aus den Admin-Schnittstellen eintragen (inkl. Secret), z. B.  
   `https://<Ihre-Domain>/api/webhook/divera?unit=1&secret=…`
2. Jeder eingehende DIVERA-Webhook wird **automatisch in der Datenbank** gespeichert (`divera_alarm_samples`) — **ohne** dass jemand die Webseite öffnet.
3. Kommt zuerst ein Webhook beim **Einsatzstart** und später einer beim **Beenden**, wird das **gleiche Beispiel aktualisiert** (vollständiges JSON inkl. `closed: true`).
4. Die Beispiele sind später unter **Testalarm** sichtbar (auch wenn der Einsatz in DIVERA längst archiviert ist). Dort können Sie mit **Einsatz starten** einen Test auf der Startseite simulieren.

**Wichtig:** Der Testmodus muss während des Einsatzes **an** bleiben, damit Webhooks als Beispiel gespeichert werden. Gespeicherte Beispiele (`divera_alarm_samples`) **bleiben in der Datenbank**, auch wenn der Testmodus später ausgeschaltet wird. Beim Testmodus-Ende werden nur laufende Test-Einsätze auf der Startseite entfernt, nicht die Beispiel-Vorlagen.

**Logs prüfen** (optional):

```bash
docker compose logs -f app | findstr /i "Divera-Beispiel"
```

Erwartung bei erfolgreichem Webhook: `Webhook gespeichert unit=… alarmId=… closed=…`

---

## Schritt 7: Prüfen, ob Divera-Daten ankommen

1. Zurück über **„Zurück zum Dashboard“** oder erneut `https://fw-manager.home.arpa/?unit=1`
2. Im Bereich **„Divera – aktuelle Einsätze“** sollten bei gültigem Key die Alarme erscheinen (oder eine leere Liste, wenn gerade kein Einsatz aktiv ist).

Optional JSON für spätere App:

- `https://fw-manager.home.arpa/api/v1/units/1/divera/alarms`

---

## Schritt 8 (optional): Polling für spätere Push-Benachrichtigungen

Standardmäßig **aus**. Du kannst es **ohne Änderung an Projektdateien** einschalten, indem du auf dem Host **vor** `docker compose up` eine Umgebungsvariable setzt:

**Linux / macOS:**

```bash
export FEUERWEHR_DIVERA_POLL_ENABLED=true
docker compose up -d --build
```

**Windows PowerShell:**

```powershell
$env:FEUERWEHR_DIVERA_POLL_ENABLED = "true"
docker compose up -d --build
```

Die Variable wird in den App-Container durchgereicht (`docker-compose.yml`). Die App loggt dann erkannte **neue** Divera-Alarm-IDs (Platzhalter für später FCM/Push).

Zum wieder Abschalten: Variable weglassen bzw. auf `false` setzen und Container neu starten:

```bash
docker compose down
docker compose up -d --build
```

---

## Kurzüberblick (Checkliste)

| Nr. | Aktion |
|-----|--------|
| 0 | `git clone https://github.com/Boede89/feuerwehr-manager.git` und `cd feuerwehr-manager` |
| 1 | `chmod +x install.sh && ./install.sh` |
| 2 | Browser: `https://fw-manager.home.arpa` |
| 3 | **Einstellungen** → **Mein Passwort** ändern |
| 4 | **Einstellungen** → **Divera**: URL prüfen, **Access Key** eintragen → **Speichern** |
| 5 | Dashboard prüfen (Divera-Liste / JSON-Endpunkt) |

---

## Bei Problemen

- **Seite lädt nicht:** Firewall, Ports 80/443, `docker compose ps`, `docker compose logs app`, `docker compose logs caddy`
- **Divera-Fehlermeldung:** Key prüfen, URL prüfen, von der App aus Internet-Zugang (Proxy?) prüfen
- **MySQL:** Port **3309** am Host nur intern nutzen; Zugangsdaten siehe `docker-compose.yml` / `README.md`

---

*Diese Anleitung setzt die Web-UI für Divera-Einstellungen voraus (`/settings/divera`).*
