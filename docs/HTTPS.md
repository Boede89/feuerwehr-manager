# HTTPS & RFID-Login

## Zugriff

Nach `docker compose up -d --build` ist die App erreichbar über:

| URL | Zweck |
|-----|--------|
| **https://fw-manager.home.arpa** | Produktiv (Caddy, Port 443) |
| **http://fw-manager.home.arpa** | Leitet auf HTTPS um (Port 80) |
| http://&lt;Server-IP&gt;:8080 | Optional direkt zur App (Debug) |

Caddy nutzt standardmäßig ein **internes Zertifikat** (`tls internal`). Beim ersten Aufruf warnt der Browser – das ist normal im LAN. Zertifikat einmal bestätigen oder für dauerhaftes Vertrauen die Caddy-Root-CA auf den Clients importieren.

## Lokaler DNS-Name

Empfohlen ist ein zentraler DNS-Eintrag im Netzwerk:

- `fw-manager.home.arpa` -> `192.168.10.123`

Beispiel mit AdGuard: **DNS rewrites**  
`fw-manager.home.arpa` => `192.168.10.123`

## .env

```bash
# Session-Cookies nur über HTTPS (Standard mit Caddy)
FEUERWEHR_SESSION_COOKIE_SECURE=true

# Ohne Caddy / lokaler Java-Start auf :8080:
# FEUERWEHR_SESSION_COOKIE_SECURE=false
```

## RFID-Login (Web Serial)

Voraussetzungen auf dem **Client-PC** (z. B. Zorin + Brave):

1. Reader im **USB-COM-Modus** (nicht Keyboard-Wedge)
2. App über **HTTPS** öffnen
3. Browser: **Chrome** oder **Brave** (Web Serial API)
4. Login → **„Lesegerät verbinden“** → COM-Port wählen (z. B. `/dev/ttyACM0`)
5. Chip im Admin registriert (`91EC5CE6` o. Ä.)
6. Chip auflegen → automatische Anmeldung (ggf. danach 2FA-Code)

Brave: unter `brave://settings/content/serialPorts` „Websites dürfen nach seriellen Ports fragen“ erlauben.

### 2FA

Hat der Benutzer TOTP aktiv, leitet RFID-Login auf `/login/totp` weiter (wie Passwort-Login).

## Eigene Domain (Let's Encrypt)

`docker/caddy/Caddyfile.domain.example` nach `Caddyfile` kopieren, Domain anpassen, DNS auf den Server zeigen lassen, `docker compose restart caddy`.

## Caddy-Root-CA auf Clients vertrauen (optional, empfohlen)

Auf dem Server:

```bash
docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt > caddy-local-root.crt
```

`caddy-local-root.crt` dann auf den jeweiligen Client importieren (Windows/Linux/Android), damit keine Zertifikatswarnung mehr erscheint.

## Fallback ohne Web Serial

`tools/rfid-bridge/rfid_bridge.py` – lokale Bridge, falls kein HTTPS/Web Serial möglich (nur Notfall).
