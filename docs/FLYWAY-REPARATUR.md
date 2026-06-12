# Flyway-Reparatur (App startet nicht / Container „Restarting“)

## V72 — Anwesenheitslisten

Symptom:

```text
Detected failed migration to version 72 (attendance reports).
```

```bash
cd /opt/feuerwehr/feuerwehr-manager
git pull
chmod +x scripts/repair-flyway-v72.sh
./scripts/repair-flyway-v72.sh
```

Das Skript legt das Schema an, markiert V72 in Flyway als erfolgreich (SQL-Typ + Checksum) und baut die App neu (`--build`), damit die korrigierte Migration im JAR liegt.

**Ursache des ersten Fehlers:** Tippfehler `unit_termines` statt `unit_termine` in V72.

---

## V37 — Atemschutz-Kursauswahl

Symptom:

```text
Detected failed migration to version 37 (AtemschutzCourseSelection).
```

## Schnellfix (empfohlen)

```bash
cd /opt/feuerwehr/feuerwehr-manager
git pull
chmod +x scripts/repair-flyway-v37.sh
./scripts/repair-flyway-v37.sh
```

Das Skript:

1. stoppt die App
2. legt das Schema per SQL an
3. markiert V37 in Flyway als **erfolgreich** (ohne die Java-Migration erneut auszuführen)
4. startet die App

Erfolg: `ffm_app` = **running**, Log: `Started FeuerwehrManagerApplication`.

Browser: `http://<Server-IP>:8080`

## Manuell (einzelne Schritte)

```bash
cd /opt/feuerwehr/feuerwehr-manager
docker compose stop app

docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-schema.sql \
  2>/dev/null || true

docker compose exec -T mysql mysql -uff -pffsecret feuerwehr_manager < scripts/repair-v37-mark-success.sql

docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success FROM flyway_schema_history WHERE version='37';"

docker compose up -d app
```

`success` muss **1** sein.

## Prüfen

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SHOW COLUMNS FROM unit_atemschutz_settings LIKE 'agt_course_id';"
```
