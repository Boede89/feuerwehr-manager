# Flyway-Reparatur (App startet nicht / Container „Restarting“)

Symptom in den Logs:

```text
Detected failed migration to version 37 (atemschutz course selection).
```

## Schnellfix auf dem Server

```bash
cd /opt/feuerwehr/feuerwehr-manager

# 1) ALLE V37-Einträge entfernen (auch erneut fehlgeschlagene)
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37';"

# 2) Fix holen und App neu bauen
git pull
docker compose up -d --build app

# 3) Prüfen (ca. 30 Sekunden warten)
docker compose ps
docker compose logs app --tail 40
```

Erfolg: `ffm_app` = **running**, Log enthält `Started FeuerwehrManagerApplication`.

Browser: `http://<Server-IP>:8080`

## Optional: Zustand prüfen

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SELECT version, success, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
   SHOW COLUMNS FROM unit_atemschutz_settings LIKE 'agt_course_id';"
```

Version 37 sollte `success = 1` sein.

## Wenn es weiterhin scheitert

Logs sichern:

```bash
docker compose logs app --tail 100
```

Manuell Schema anlegen (Fehler „Duplicate column“ ignorieren):

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager
```

```sql
ALTER TABLE unit_atemschutz_settings ADD COLUMN agt_course_id BIGINT NULL;
ALTER TABLE unit_atemschutz_settings
  ADD CONSTRAINT fk_unit_atemschutz_course
  FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL;
DELETE FROM flyway_schema_history WHERE version = '37';
```

Dann erneut `git pull` und `docker compose up -d --build app`.
