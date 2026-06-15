# RFID-Bridge (Fallback)

Nur nötig, wenn **kein HTTPS** oder **kein Web Serial** im Browser verfügbar ist.

Standard: RFID-Login direkt in der Login-Seite über **Web Serial** (siehe `docs/HTTPS.md`).

## Start

```bash
sudo apt install python3-serial
python3 rfid_bridge.py --device /dev/ttyACM0
```

Die Login-Seite müsste dann für die Bridge angepasst werden (ältere Variante) – bei HTTPS bitte Web Serial nutzen.
