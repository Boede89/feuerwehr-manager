-- Bestehende Konten: Anmelde-E-Mail aus verknüpftem Personen-Datensatz übernehmen

UPDATE users u
INNER JOIN persons p ON p.user_id = u.id
    AND p.anonymized_at IS NULL
    AND p.email IS NOT NULL
    AND TRIM(p.email) <> ''
SET u.login_email = LOWER(TRIM(p.email))
WHERE u.login_email IS NULL
  AND u.anonymized_at IS NULL;
