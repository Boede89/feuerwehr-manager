# Proxmox / Server: Ratgeber (Container, OS, Ressourcen)

## Wie viele „Container“ brauche ich?

| Ebene | Empfehlung |
|--------|------------|
| **Proxmox** | **Ein** LXC (oder eine VM) als **Docker-Host** reicht für den Start. |
| **Docker (innerhalb dieses Hosts)** | **Zwei** Services aus einem `docker compose`: **MySQL** + **Feuerwehr-Manager-App**. Die legt `docker compose up` automatisch an – du musst in Proxmox **keine** zwei getrennten LXC für App und DB anlegen. |

Zwei **Proxmox**-LXC (einer nur DB, einer nur App) sind nur sinnvoll, wenn du später strikte Trennung, andere Backup-Zyklen oder mehrere Apps auf demselben DB-Server willst – für den normalen Vereinsbetrieb ist **ein LXC + Docker Compose** einfacher.

---

## Debian oder Ubuntu?

| | **Ubuntu 22.04 LTS** | **Debian 12 (Bookworm)** |
|--|----------------------|----------------------------|
| **Docker-Doku / How-Tos** | Sehr viele Schritt-für-Schritt-Anleitungen | Gut, etwas seltener in „Copy-Paste“-Tutorials |
| **Paketstand** | Etwas frischer | Sehr stabil, etwas konservativer |
| **Empfehlung** | Wenn du wenig Zeit für Fehlersuche willst: **Ubuntu 22.04 LTS** | Wenn du bewusst schlank und „klassisch“ willst: **Debian 12** |

Beides ist für Docker + Java + MySQL **völlig in Ordnung**. Wichtiger als die Distribution: **Docker offiziell installieren** (Repository von Docker, nicht nur die distro-alte `docker.io`-Version, falls du aktuelle Compose-Features brauchst).

**Proxmox-Template:** „Ubuntu 22.04“ oder „Debian 12“ **unprivileged** LXC, ** nesting** nur aktivieren, wenn du Docker im LXC nutzt (Proxmox: LXC-Option **nesting** / ggf. **keyctl** – je nach Proxmox-Doku für Docker in LXC).

---

## Ressourcen (RAM, CPU, Disk)

| Ressource | Minimum | Empfohlen (komfortabel) |
|-----------|---------|---------------------------|
| **RAM** | 2 GB | **4 GB** (MySQL + JVM + OS) |
| **vCPU** | 2 | **2–4** |
| **Disk** | 15 GB frei | **25–40 GB** (Images, Maven-Build im Docker-Image, MySQL-Daten, Logs) |

Weniger als **2 GB RAM** führt schnell zu OOM beim ersten `docker compose build`.

---

## Ablauf auf dem Server (Kurz)

1. LXC/VM mit Ubuntu 22.04 oder Debian 12, Docker + Compose-Plugin installieren.  
2. Repository klonen:  
   `git clone https://github.com/Boede89/feuerwehr-manager.git`  
   `cd feuerwehr-manager`  
3. **Ein Befehl:**  
   `chmod +x install.sh && ./install.sh`  
4. Im Browser **Einstellungen / Divera** wie in `docs/SCHRITT-FUER-SCHRITT.md`.

---

## Sicherheit (Kurz)

- Passwörter in `docker-compose.yml` (MySQL) sind **Beispielwerte** – für Produktion **ändern** (Umgebungsdatei `.env` + `docker compose` ohne Secrets im Git, siehe spätere Doku).  
- Port **8080** und **3309** nur im vertrauenswürdigen Netz freigeben oder hinter Reverse Proxy + TLS.

---

*Ergänzung zu `docs/SCHRITT-FUER-SCHRITT.md` und `README.md`.*
