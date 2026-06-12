DELETE FROM flyway_schema_history WHERE version = '72';

SET @next_rank := (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history);

INSERT INTO flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
) VALUES (
    @next_rank,
    '72',
    'attendance reports',
    'JDBC',
    'db.migration.V72__attendance_reports',
    NULL,
    'manual-repair',
    NOW(),
    0,
    1
);
