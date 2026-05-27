# Debian 12 – Vorbereitung vor `git clone`

Diese Anleitung beschreibt alles, was du **auf dem frischen Debian-Container (LXC/VM)** erledigen musst, **bevor** du das Repository klonst und `./install.sh` ausführst.

Repository (danach): **https://github.com/Boede89/feuerwehr-manager**

---

## Übersicht

| Phase | Wo |
|--------|-----|
| 1 | Proxmox: LXC anlegen (Debian 12) |
| 2 | Im LXC: System aktualisieren, Basis-Pakete |
| 3 | Docker + Docker Compose Plugin installieren |
| 4 | Optional: Firewall, Zeitzone |
| 5 | **Erst jetzt:** `git clone` und `install.sh` → siehe [SCHRITT-FUER-SCHRITT.md](SCHRITT-FUER-SCHRITT.md) |

---

## Phase 1: Proxmox – LXC anlegen (Kurz)

| Einstellung | Empfehlung |
|-------------|------------|
| **Template** | Debian 12 (Bookworm) – Standard oder „debian-12-standard“ |
| **Typ** | LXC (unprivileged ist üblich) |
| **RAM** | 4096 MB (Minimum 2048 MB) |
| **CPU** | 2 Kerne (4 wenn der Host es hergibt) |
| **Disk** | 32 GB (Minimum ~20 GB) |
| **Netz** | DHCP oder feste IP – notiere die IP für den Browser später |

**Docker im LXC:** In den LXC-Optionen unter **Features** aktivieren:

- **nesting** = an (Pflicht für Docker in LXC)
- **keyctl** = an (bei Proxmox oft nötig für Docker)

Ohne **nesting** schlägt `docker compose` später oft fehl.

Nach dem Erstellen: Container starten, per Konsole oder SSH einloggen (Benutzer `root` oder dein angelegter User mit `sudo`).

---

## Phase 2: System vorbereiten (als root oder mit sudo)

Alle folgenden Befehle im **Debian-LXC** ausführen.

### 2.1 Paketlisten aktualisieren und Basis installieren

```bash
apt update
apt upgrade -y
apt install -y ca-certificates curl gnupg git
```

- **git** – für `git clone` gleich danach  
- **curl / gnupg / ca-certificates** – für die offizielle Docker-Installation  

### 2.2 Zeitzone (empfohlen)

```bash
timedatectl set-timezone Europe/Berlin
timedatectl
```

Die App nutzt `Europe/Berlin` in der Datenbank-Konfiguration – gleiche Zeitzone vermeidet Verwirrung bei Logs und Terminen.

### 2.3 Optional: eigenen Benutzer statt nur root

Wenn du nicht dauerhaft als `root` arbeiten willst (empfohlen für den Alltag):

```bash
adduser feuerwehr
usermod -aG sudo feuerwehr
```

Danach als `feuerwehr` einloggen und bei den nächsten Befehlen `sudo` voranstellen, wo nötig. Für Docker muss der User in der Gruppe **docker** (kommt in Phase 3).

---

## Phase 3: Docker installieren (offizielles Docker-Repository)

**Nicht** nur das alte Paket `docker.io` aus Debian ohne Compose-Plugin – für dieses Projekt brauchst du **`docker compose`** (Plugin).

### 3.1 Docker GPG-Key und Repository

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian bookworm stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt update
```

*(Bei **Debian 13** statt `bookworm` in der Zeile `trixie` verwenden – nur wenn dein Template wirklich Debian 13 ist.)*

### 3.2 Docker Engine + Compose Plugin installieren

```bash
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

### 3.3 Docker-Dienst starten und Autostart

```bash
systemctl enable --now docker
systemctl status docker
```

Status sollte **active (running)** zeigen. Beenden mit `q`.

### 3.4 Prüfen, ob Compose funktioniert

```bash
docker compose version
```

Erwartung: eine Versionszeile (z. B. `Docker Compose version v2.x`).

### 3.5 Benutzer zur Gruppe `docker` (wenn du nicht als root weiterarbeitest)

```bash
usermod -aG docker feuerwehr
```

**Wichtig:** Danach **abmelden und neu einloggen** (oder LXC neu starten), damit die Gruppe greift. Ohne Neuanmeldung: `docker` nur mit `sudo`.

Kurztest:

```bash
docker run --rm hello-world
```

Wenn „Hello from Docker!“ erscheint, ist Docker bereit.

---

## Phase 4: Optional, aber sinnvoll

### 4.1 Firewall (ufw)

Nur wenn du eine Firewall nutzen willst und Port **8080** von anderen PCs im Netz erreichbar sein soll:

```bash
apt install -y ufw
ufw allow OpenSSH
ufw allow 8080/tcp
ufw enable
ufw status
```

SSH-Port anpassen, falls du keinen Standard-Port nutzt.

### 4.2 Arbeitsverzeichnis

```bash
mkdir -p /opt/feuerwehr
cd /opt/feuerwehr
```

Hier kannst du gleich im nächsten Schritt klonen (`/opt/feuerwehr/feuerwehr-manager`).

### 4.3 Speicherplatz prüfen

```bash
df -h /
free -h
```

Mindestens **~5 GB frei** auf `/` für Images und ersten Build; **4 GB RAM** sichtbar.

---

## Phase 5: Jetzt erst Git und Installation

Wenn Phase 2–3 (und optional 4) ohne Fehler durch sind:

```bash
cd /opt/feuerwehr
git clone https://github.com/Boede89/feuerwehr-manager.git
cd feuerwehr-manager
chmod +x install.sh
./install.sh
```

Weiter mit Browser und Web-UI: **[SCHRITT-FUER-SCHRITT.md](SCHRITT-FUER-SCHRITT.md)** (Divera unter **Einstellungen**).

---

## Checkliste „Bereit für git clone?“

- [ ] Debian 12 LXC läuft, **nesting** (und ggf. **keyctl**) aktiv  
- [ ] `apt update && apt upgrade` erledigt  
- [ ] `git`, `curl`, `ca-certificates` installiert  
- [ ] `docker compose version` funktioniert  
- [ ] `docker run hello-world` erfolgreich  
- [ ] Zeitzone `Europe/Berlin` (optional, empfohlen)  
- [ ] Port **8080** erreichbar (Firewall / Proxmox, falls nötig)  

Erst wenn alle Punkte für dich passen → **Phase 5**.

---

## Häufige Probleme

| Problem | Lösung |
|---------|--------|
| `Cannot connect to the Docker daemon` | `systemctl start docker`; User in Gruppe `docker` + neu einloggen |
| `permission denied` bei Docker | `sudo` nutzen oder Gruppe `docker` + Neuanmeldung |
| `docker compose` unbekannt | Paket `docker-compose-plugin` installieren (Phase 3.2) |
| Build bricht mit „no space left“ ab | Disk vergrößern oder `docker system prune` (Vorsicht: löscht ungenutzte Images) |
| Seite :8080 von außen nicht erreichbar | Proxmox-Firewall, Host-Firewall, `ufw`, Router – Port 8080 freigeben |

---

## Kurzreferenz (nur die Befehlskette)

```bash
apt update && apt upgrade -y
apt install -y ca-certificates curl gnupg git
timedatectl set-timezone Europe/Berlin
# Docker Repository + install (Phase 3.1–3.2)
systemctl enable --now docker
docker compose version
docker run --rm hello-world
cd /opt/feuerwehr
git clone https://github.com/Boede89/feuerwehr-manager.git
cd feuerwehr-manager && chmod +x install.sh && ./install.sh
```

---

*Debian 12 (Bookworm). Allgemeiner Proxmox-Rat: [PROXMOX-RATGEBER.md](PROXMOX-RATGEBER.md).*
