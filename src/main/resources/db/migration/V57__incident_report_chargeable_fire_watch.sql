ALTER TABLE incident_reports
    ADD COLUMN chargeable BOOLEAN NULL AFTER eigentuemer,
    ADD COLUMN fire_watch BOOLEAN NULL AFTER chargeable;
