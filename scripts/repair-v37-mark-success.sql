DELETE FROM flyway_schema_history WHERE version = '37';

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
    '37',
    'atemschutz course selection',
    'SQL',
    'V37__atemschutz_course_selection.sql',
    -530345842,
    'manual-repair',
    NOW(),
    0,
    1
);
