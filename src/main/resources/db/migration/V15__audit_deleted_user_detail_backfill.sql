-- Namen gelöschter Benutzer im Audit-Log nachziehen (aus verknüpftem Personal-Stamm)
UPDATE audit_events ae
INNER JOIN (
    SELECT p.user_id, MIN(p.id) AS person_id
    FROM persons p
    WHERE p.user_id IS NOT NULL
    GROUP BY p.user_id
) pick ON pick.user_id = ae.subject_user_id
INNER JOIN persons p ON p.id = pick.person_id
SET ae.detail = TRIM(CONCAT(TRIM(p.first_name), ' · ', TRIM(p.last_name)))
WHERE ae.event_type = 'USER_ANONYMIZED'
  AND ae.subject_user_id IS NOT NULL
  AND (ae.detail IS NULL OR TRIM(ae.detail) = '');
