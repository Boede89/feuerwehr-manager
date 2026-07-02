# Feuerwehr Einsatz-App (Android)

Android-Client für das Modul **Einsatz-App** im Feuerwehr-Manager.

## Server-Adresse in der App

Die **Server-URL wird in der App eingetragen** — nicht fest im Code (außer optionalem Standard beim ersten Start).

1. Beim **ersten Start** auf dem Login-Bildschirm: Feld **„Server-Adresse“**
   - z. B. `https://fw-manager.home.arpa`
   - oder `http://192.168.1.10:8080` (nur Test/LAN)
2. **„Verbindung testen“** prüft `/actuator/health`
3. Die Adresse wird **lokal gespeichert** (DataStore)
4. Nach Login: unter **„Server-Adresse ändern“** auf der Startseite anpassbar (danach neu anmelden)

Optionaler Standard beim ersten Öffnen: `local.properties` → `default.server.url=…` (siehe `local.properties.example`).

## Android Studio einrichten

1. **Firebase** → Android-App mit Package `de.feuerwehr.einsatzapp` anlegen
2. `google-services.json` nach `app/google-services.json` kopieren (Vorlage: `google-services.json.example`)
3. Android Studio: **File → Open** → Ordner `android/einsatzapp`
4. Gradle Sync, dann auf Gerät installieren

## Ablauf in der App

1. Server-Adresse eintragen & testen
2. Mit Feuerwehr-Manager-Benutzer anmelden
3. FCM-Token wird automatisch registriert (`POST /api/v1/einsatzapp/devices`)
4. Push bei DIVERA-Einsatz → Tipp öffnet Einsatzdetails
5. Liste offener Einsätze per Pull/Aktualisieren

**Dauerhafte Anmeldung:** Zugangsdaten werden verschlüsselt auf dem Gerät gespeichert; bei abgelaufener Session meldet die App sich automatisch wieder an (kein 2FA). Server-Session: 30 Tage Inaktivität.

## Voraussetzungen (Server)

- Modul **Einsatz-App** aktiv
- Push pro Einheit aktiv
- Firebase-Dienstkonto im Feuerwehr-Manager hochgeladen
- Benutzer mit Berechtigung `einsatzapp.read` und zugewiesener Einheit

## 2FA

Nutzer mit aktivierter 2FA können sich in der App **noch nicht** anmelden — Meldung mit Hinweis auf Web-Login. Phase 4 optional.

Weitere Details: `docs/EINSATZ-APP.md`
