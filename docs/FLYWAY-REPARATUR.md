# Flyway-Reparatur (App startet nicht / Container „Restarting“)

Symptom:

```text
Detected failed migration to version 37 (atemschutz course selection).
```

## Wichtig

Zuerst die App **stoppen**, sonst legt der Neustart bei jedem Versuch erneut einen fehlgeschlagenen V37-Eintrag an.

## Schnellfix (empfohlen)

```bash
cd /opt/feuerwehr/feuerwehr-manager
git pull
chmod +x scripts/repair-flyway-v37.sh
./scripts/repair-flyway-v37.sh
```

Erfolg: `ffm_app` = **running**, Log: `Started FeuerwehrManagerApplication`.

Browser: `http://<Server-IP>:8080`

## Manuell (falls Skript nicht verfügbar)

```bash
cd /opt/feuerwehr/feuerwehr-manager

docker compose stop app

docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37';"

git pull
docker compose up -d --build app

sleep 35
docker compose ps
docker compose logs app --tail 40
```

## Prüfen

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success, script FROM flyway_schema_history WHERE version='37';
   SHOW COLUMNS FROM unit_atemschutz_settings LIKE 'agt_course_id';"
```

Version 37 sollte `success = 1` sein (Java-Migration `V37__AtemschutzCourseSelection`).
