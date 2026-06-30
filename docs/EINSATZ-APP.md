# Modul Einsatz-App

Android-Alarmierung: Wenn DIVERA einen Einsatz meldet, erhalten registrierte Geräte eine Push-Benachrichtigung in der Feuerwehr-App.

## Phase 2 (aktuell)

| Komponente | Status |
|------------|--------|
| Modul `einsatzapp` in Admin → Module | aktiv |
| Push-Einstellungen pro Einheit (Admin → Schnittstellen) | aktiv |
| FCM-Versand bei DIVERA-Webhook | aktiv |
| Geräte-Token (REST-API) | aktiv |
| REST-Login für Android (`POST /api/v1/auth/login`) | aktiv |
| Android-App (Phase 3) | folgt |

## Architektur

1. **DIVERA** → Webhook an Feuerwehr-Manager (`POST /api/webhook/divera?unit=…`)
2. **Backend** parst Alarm, prüft Modul + `push_enabled` + FCM-Konfiguration
3. **Firebase Cloud Messaging (FCM)** → registrierte Android-Geräte
4. **App** lädt Details über `GET /api/v1/units/{unitId}/divera/alarms`

Die App soll **nicht** dauerhaft die DIVERA-API pollen. Zentraler Versand über den Server — siehe auch `docs/DIVERA.md`.

Push wird nur gesendet wenn:

- Modul `einsatzapp` für die Einheit aktiv ist
- `push_enabled` in den Einsatz-App-Einstellungen (Admin → Schnittstellen)
- FCM serverseitig konfiguriert (`FEUERWEHR_FCM_ENABLED=true`, `FEUERWEHR_FCM_SERVER_KEY`)
- Einsatz nicht geschlossen (`closed=false`)
- Gerät registriert und Nutzer hat `einsatzapp.read`

## FCM einrichten (Server)

1. Firebase-Projekt anlegen, Android-App registrieren (Phase 3)
2. **Legacy Server Key** aus Firebase Console → Cloud Messaging
3. In `.env` setzen (nicht ins Git committen):

```env
FEUERWEHR_FCM_ENABLED=true
FEUERWEHR_FCM_SERVER_KEY=<Firebase Legacy Server Key>
```

4. `docker compose up -d --build`

## REST-API (Android)

### Anmeldung

`POST /api/v1/auth/login`

```json
{ "username": "max", "password": "…" }
```

Antwort bei Erfolg: Session-Cookie (`JSESSIONID`). Bei aktivierter 2FA: `totpRequired: true` (App muss Web-TOTP nutzen).

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

### Gerät abmelden

`DELETE /api/v1/einsatzapp/devices`

```json
{ "fcmToken": "…" }
```

### Push-Payload (data)

| Feld | Bedeutung |
|------|-----------|
| `type` | `divera_alarm` |
| `alarmId` | DIVERA-Alarm-ID (long) |

## Modul aktivieren

1. Adminpanel → Einheit → **Module** → „Einsatz-App“ aktivieren
2. Adminpanel → Einheit → **Schnittstellen** → Push aktivieren
3. Unter **Rollen** Berechtigung „Einsatz-App“ vergeben (Lesen für Push-Empfang)

## Nächste Schritte

- **Phase 3:** Android-App (Login, Token-Registrierung, Push + Einsatzansicht)
