-- Standard-Startseiten-Layout der Einheit (für neue Benutzer / Admin-Vorlage).
ALTER TABLE units
    ADD COLUMN default_dashboard_layout_json TEXT NULL;
