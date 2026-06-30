# Modul Einsatz-App

Android-Alarmierung: Wenn DIVERA einen Einsatz meldet, erhalten registrierte Geräte eine Push-Benachrichtigung in der Feuerwehr-App.

## Phase 2 (aktuell)

| Komponente | Status |
|------------|--------|
| Modul `einsatzapp` in Admin → Module | aktiv |
| Push-Einstellungen pro Einheit (Admin → Schnittstellen) | aktiv |
| FCM-Versand bei DIVERA-Webhook (HTTP v1) | aktiv |
| Geräte-Token (REST-API) | aktiv |
| REST-Login für Android (`POST /api/v1/auth/login`) | aktiv |
| Android-App (Phase 3) | folgt |

## Zwei verschiedene Firebase-Dateien

| Datei | Wo | Wofür |
|-------|-----|--------|
| **Dienstkonto-JSON** | Server (`secrets/fcm-service-account.json`) | Backend sendet Push |
| **google-services.json** | Android-Projekt (Android Studio) | App empfängt Push — kommt in Phase 3 |

Die heruntergeladene JSON vom Tab **Dienstkonten** ist **nur für den Server**.  
Für Android Studio brauchst du später eine **zweite** Datei aus Firebase → Android-App anlegen → `google-services.json`.

## Architektur

1. **DIVERA** → Webhook an Feuerwehr-Manager (`POST /api/webhook/divera?unit=…`)
2. **Backend** parst Alarm, prüft Modul + `push_enabled` + FCM-Konfiguration
3. **Firebase Cloud Messaging (HTTP v1)** → registrierte Android-Geräte
4. **App** lädt Details über `GET /api/v1/units/{unitId}/divera/alarms`

Push wird nur gesendet wenn:

- Modul `einsatzapp` für die Einheit aktiv ist
- `push_enabled` in den Einsatz-App-Einstellungen (Admin → Schnittstellen)
- FCM serverseitig konfiguriert (`FEUERWEHR_FCM_ENABLED=true` + Dienstkonto-JSON)
- Einsatz nicht geschlossen (`closed=false`)
- Gerät registriert und Nutzer hat `einsatzapp.read`

## FCM einrichten (Server)

### 1. Dienstkonto-JSON aus Firebase

1. [Firebase Console](https://console.firebase.google.com) → Projekt
2. **Projekteinstellungen** → **Dienstkonten**
3. **Neuen privaten Schlüssel generieren** → JSON herunterladen

### 2. JSON auf den Server legen

Im Projektordner auf dem Server (nicht ins Git):

```bash
mkdir -p secrets
cp ~/Downloads/dein-projekt-firebase-adminsdk-xxxxx.json secrets/fcm-service-account.json
chmod 600 secrets/fcm-service-account.json
```

### 3. `.env` anpassen

```env
FEUERWEHR_FCM_ENABLED=true
FEUERWEHR_FCM_SERVICE_ACCOUNT_PATH=/data/fcm-service-account.json
```

`docker-compose.yml` — Zeile unter `app.volumes` einkommentieren:

```yaml
- ./secrets/fcm-service-account.json:/data/fcm-service-account.json:ro
```

Alternativ nach dem Start: `docker cp secrets/fcm-service-account.json ffm_app:/data/fcm-service-account.json`

### 4. Deploy

```bash
docker compose up -d --build
```

In der Web-UI unter **Einsatz-App** sollte „FCM: Konfiguriert“ erscheinen.

## REST-API (Android)

### Anmeldung

`POST /api/v1/auth/login`

```json
{ "username": "max", "password": "…" }
```

Antwort bei Erfolg: Session-Cookie (`JSESSIONID`). Bei aktivierter 2FA: `totpRequired: true`.

### Gerät registrieren

`POST /api/v1/einsatzapp/devices` (authentifiziert)

```json
{
  "unitId": 1,
  "fcmToken": "…",
  "deviceLabel": "Pixel 8",
  "platform": "android"
}
```

Das `fcmToken` kommt **von der Android-App** (Phase 3), nicht aus der Dienstkonto-JSON.

### Gerät abmelden

`DELETE /api/v1/einsatzapp/devices`

```json
{ "fcmToken": "…" }
```

### Push-Payload (data)

| Feld | Bedeutung |
|------|-----------|
| `type` | `divera_alarm` |
| `alarmId` | DIVERA-Alarm-ID (String) |

## Modul aktivieren

1. Adminpanel → Einheit → **Module** → „Einsatz-App“ aktivieren
2. Adminpanel → Einheit → **Schnittstellen** → Push aktivieren
3. Unter **Rollen** Berechtigung „Einsatz-App“ vergeben (Lesen für Push-Empfang)

## Phase 3 (Android Studio)

1. Firebase → **App hinzufügen** → Android
2. Package-Name eintragen (z. B. `de.feuerwehr.einsatzapp`)
3. `google-services.json` in Android Studio ins `app/`-Modul legen
4. App: Login → FCM-Token holen → `POST /api/v1/einsatzapp/devices`

Die Server-JSON und `google-services.json` gehören zum **selben Firebase-Projekt**, sind aber **unterschiedliche Dateien**.
