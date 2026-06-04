# Divera-Anbindung (Feuerwehr-Manager)

## API (wie bisherige PHP-Anwendung)

- **Abruf:**  
  `GET {api_base_url}/api/v2/alarms?accesskey={ACCESS_KEY}` (nicht archivierte Alarme)
- **Anzeige:** Im Produktivbetrieb werden geschlossene Einsätze ausgeblendet; im Testmodus alle gelieferten Alarme angezeigt.
- **Testmodus-Beispiele:** Beim API-Abruf und bei jedem eingehenden **Webhook** (`POST /api/webhook/divera`) wird das vollständige JSON in MySQL gespeichert — **24/7**, ohne geöffnete Webseite. Jeder weitere Webhook zum gleichen Einsatz (z. B. **beendet**) **aktualisiert** den Datensatz. Später geplant: Modul **Einsatzberichte** (nutzt dann diese gespeicherten Daten).
- **Standard-Basis-URL:** `https://app.divera247.com`  
  (anpassbar pro Einheit in `unit_divera_settings.api_base_url`)

Die Antwort wird wie in der PHP-Funktion `fetch_divera_alarms` ausgewertet: JSON mit `data.items` oder Array unter `data`.

## Dieses Projekt

| Komponente | Ort |
|------------|-----|
| HTTP-Client + Parser | `de.feuerwehr.manager.divera.DiveraApiClient` |
| Einheit + Zugangsdaten | `Unit`, `UnitDiveraSettings` (+ `webhook_secret`, Flyway `V18`) |
| Webhook (Push von DIVERA) | `POST /api/webhook/divera?unit={id}&secret=…` |
| Admin: Test / Import | `POST /admin/rest/unit/divera/test`, `…/import` |
| JSON für Android/extern | `GET /api/v1/units/{unitId}/divera/alarms` |
| Optional Polling | `DiveraPollScheduler` bei `feuerwehr.divera.poll-enabled=true` |

## Später: Push-Benachrichtigung (Android)

Typischer Ablauf:

1. **Server** erkennt neuen Einsatz (Polling wie oben, oder Webhook falls Divera das anbietet / eigener Worker).
2. **Outbox** oder Queue speichert „Benutzer X soll Push zu Alarm Y“.
3. **FCM** (Firebase Cloud Messaging) sendet an registrierte Geräte-Token.

Direkt aus der Android-App **nicht** dauerhaft gegen die Divera-API pollen (Akku, Hintergrundlimits) – besser zentral im Backend.

## Referenz extern

- Divera API-Dokumentation: [https://api.divera247.com/](https://api.divera247.com/)
