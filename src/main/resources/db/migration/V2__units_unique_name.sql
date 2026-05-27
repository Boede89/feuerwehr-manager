-- Eindeutige Namen pro Einheit (Groß/Kleinschreibung wird ignoriert – Prüfung zusätzlich in der App)
CREATE UNIQUE INDEX uk_units_name ON units (name);
