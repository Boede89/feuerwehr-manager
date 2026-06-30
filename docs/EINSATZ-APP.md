# Modul Einsatz-App

Android-Alarmierung: Wenn DIVERA einen Einsatz meldet, erhalten registrierte Geräte eine Push-Benachrichtigung in der Feuerwehr-App.

## Phase 1 (aktuell)

| Komponente | Status |
|------------|--------|
| Modul `einsatzapp` in Admin → Module | aktiv |
| Navigation „Einsatz-App“ | Platzhalter-Seite |
| Rollen-Berechtigungen `einsatzapp.read` / `einsatzapp.write` | aktiv |
| Android-App, FCM, Geräteverwaltung | folgt |

## Architektur (Ziel)

1. **DIVERA** → Webhook an Feuerwehr-Manager (`POST /api/webhook/divera`)
2. **Backend** erkennt neuen/geänderten Einsatz, legt Push in Outbox
3. **Firebase Cloud Messaging (FCM)** → Android-Geräte
4. **App** zeigt Alarm, lädt Details über REST (`/api/v1/units/{unitId}/divera/alarms`)

Die App soll **nicht** dauerhaft die DIVERA-API pollen (Akku, Hintergrundlimits). Zentraler Versand über den Server — siehe auch `docs/DIVERA.md`.

## Nächste Schritte (geplant)

- **Phase 2:** Geräte-Token speichern, FCM-Konfiguration, Push bei DIVERA-Webhook
- **Phase 3:** Android-App (Login, Token-Registrierung, Push + Einsatzansicht)

## Modul aktivieren

1. Adminpanel → Einheit → **Module** → „Einsatz-App“ aktivieren
2. Unter **Rollen** Berechtigung „Einsatz-App“ vergeben (Lesen für Anzeige, Schreiben für spätere Verwaltung)
