# Flyway-Reparatur (App startet nicht / Container „Restarting“)

Symptom in den Logs:

```text
Detected failed migration to version 37 (atemschutz course selection).
Please remove any half-completed changes then run repair to fix the schema history.
```

## Schnellfix auf dem Server

Im Projektordner (`/opt/feuerwehr/feuerwehr-manager`):

```bash
# 1) Fehlgeschlagenen Eintrag aus der Flyway-Historie entfernen
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "DELETE FROM flyway_schema_history WHERE version = '37' AND success = 0;"

# 2) Neuesten Code holen (enthält idempotente V37-Migration)
git pull

# 3) App neu bauen und starten
docker compose up -d --build app

# 4) Logs prüfen
docker compose logs app --tail 40
```

Erfolg: Zeile `Started FeuerwehrManagerApplication` und `docker compose ps` zeigt **running** (nicht Restarting).

## Optional: Schema prüfen

```bash
docker compose exec mysql mysql -uff -pffsecret feuerwehr_manager -e \
  "SHOW COLUMNS FROM unit_atemschutz_settings LIKE 'agt_course_id';
   SELECT version, success, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

Die Spalte `agt_course_id` sollte existieren; Version 37 in `flyway_schema_history` mit `success = 1`.

## Wenn es danach noch hakt

```bash
docker compose logs app --tail 100
```

Die letzten Zeilen speichern und als Issue melden.
