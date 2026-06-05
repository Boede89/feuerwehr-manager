-- Anzeigenamen verknüpfter Benutzer: Nachname Vorname (wie in Tabellen)

UPDATE users u
INNER JOIN persons p ON p.user_id = u.id
    AND p.anonymized_at IS NULL
SET u.display_name = TRIM(CONCAT(p.last_name, ' ', p.first_name))
WHERE u.anonymized_at IS NULL;
