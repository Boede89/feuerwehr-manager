# Eigene Alarm-MP3s für die Einsatz-App

Legen Sie hier Ihre **MP3-Dateien** ab (beliebige Dateinamen), z. B.:

- `Feuerwehr Alarm.mp3`
- `Pager kurz.mp3`
- `Sirene.mp3`

Dann im Repository-Root ausführen:

**Windows (PowerShell):**

```powershell
.\scripts\install-alarm-tones.ps1
```

**Linux / macOS:**

```bash
bash scripts/install-alarm-tones.sh
```

Das Skript kopiert die Dateien nach `app/src/main/res/raw/` und benennt sie um in `tone_<name>.mp3` (nur Kleinbuchstaben, Zahlen und Unterstriche — Android-Vorgabe).

## Anzeigenamen in der App

Standard: aus dem Dateinamen abgeleitet (`tone_pager_kurz` → „Pager Kurz“).

Feste Namen für `tone_alarm_1.mp3` … `tone_alarm_3.mp3` können in  
`app/src/main/res/values/strings.xml` gesetzt werden.

## App neu bauen

Android Studio: **Build → Rebuild Project**, APK installieren.

In der App: **Einstellungen → Push & Alarmton** → Bereich **„App-Töne (Feuerwehr-Manager)“**.
