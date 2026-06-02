# Schritt-für-Schritt: Feuerwehr-Manager starten und einrichten

Repository: **https://github.com/Boede89/feuerwehr-manager**

Ziel: **Keine Projektdateien von Hand bearbeiten.** Du klonst das Repo, führst **einen Installationsbefehl** aus und trägst **Divera** in der **Web-Oberfläche** ein.

---

## Schritt 0: Code auf den Server holen

Auf dem Linux-Server (oder im Proxmox-LXC):

```bash
git clone https://github.com/Boede89/feuerwehr-manager.git
cd feuerwehr-manager
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

## Schritt 2: Installation (ein Befehl)

```bash
chmod +x install.sh && ./install.sh
```

*(Entspricht `docker compose up -d --build` – siehe `install.sh`.)*

---

## Schritt 3 (optional): Prüfen, ob alles läuft

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

## Schritt 4: Web-Oberfläche öffnen

Im Browser (vom gleichen Netz aus, ggf. Firewall-Port **8080** freigeben):

- **Lokal am Server:** `http://localhost:8080`
- **Von einem anderen PC:** `http://<IP-des-Servers>:8080`

Du wirst zur **Anmeldung** weitergeleitet (`/login`).

**Erster Login (nur wenn noch kein Benutzer existiert):**

| Feld | Standard (Entwicklung) |
|------|-------------------------|
| Benutzername | `admin` |
| Passwort | `changeme` (in Produktion vor dem ersten Start `FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD` setzen) |

Danach **Datenschutzhinweis bestätigen**, dann erscheint das **Dashboard** (Layout Variante D) und die **Divera**-Liste.

Details: [LOGIN.md](LOGIN.md), [DSGVO.md](DSGVO.md).

---

## Schritt 5: Divera in der Web-UI eintragen (keine Datei-Änderung)

1. Im Dashboard auf die Kachel **„Einstellungen“** tippen/klicken  
   **oder** direkt aufrufen:  
   `http://<IP>:8080/settings/divera?unit=1`
2. **API-Basis-URL** prüfen: in der Regel **`https://app.divera247.com`** (sofern Divera nichts anderes vorgibt).
3. **Divera Access Key** eintragen (aus Divera 24/7 für die jeweilige Einheit).
4. **„Speichern“** drücken.

Hinweise auf der Seite:

- Ist bereits ein Key gespeichert, bleibt er erhalten, wenn du das Passwortfeld **leer** lässt und nur die URL änderst.
- Zum **Ändern** des Keys einfach einen **neuen** Wert eintragen und speichern.

---

## Schritt 6: Prüfen, ob Divera-Daten ankommen

1. Zurück über **„Zurück zum Dashboard“** oder erneut `http://<IP>:8080/?unit=1`
2. Im Bereich **„Divera – aktuelle Einsätze“** sollten bei gültigem Key die Alarme erscheinen (oder eine leere Liste, wenn gerade kein Einsatz aktiv ist).

Optional JSON für spätere App:

- `http://<IP>:8080/api/v1/units/1/divera/alarms`

---

## Schritt 7 (optional): Polling für spätere Push-Benachrichtigungen

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
| 2 | Browser: `http://<Server>:8080` |
| 3 | **Einstellungen** (Kachel) → **Divera**: URL prüfen, **Access Key** eintragen → **Speichern** |
| 4 | Dashboard prüfen (Divera-Liste / JSON-Endpunkt) |

---

## Bei Problemen

- **Seite lädt nicht:** Firewall, Port 8080, `docker compose ps`, `docker compose logs app`
- **Divera-Fehlermeldung:** Key prüfen, URL prüfen, von der App aus Internet-Zugang (Proxy?) prüfen
- **MySQL:** Port **3309** am Host nur intern nutzen; Zugangsdaten siehe `docker-compose.yml` / `README.md`

---

*Diese Anleitung setzt die Web-UI für Divera-Einstellungen voraus (`/settings/divera`).*
