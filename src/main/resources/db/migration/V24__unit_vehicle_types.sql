CREATE TABLE unit_vehicle_types (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id    BIGINT       NOT NULL,
    type_key   VARCHAR(50)  NOT NULL,
    label      VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_vehicle_types_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_unit_vehicle_types_key UNIQUE (unit_id, type_key),
    INDEX idx_unit_vehicle_types_unit (unit_id, sort_order, label)
);

INSERT INTO unit_vehicle_types (unit_id, type_key, label, sort_order)
SELECT u.id, d.type_key, d.label, d.sort_order
FROM units u
CROSS JOIN (
    SELECT 'lkw' AS type_key, 'LKW' AS label, 1 AS sort_order
    UNION ALL SELECT 'loeschfahrzeug', 'Löschfahrzeug', 2
    UNION ALL SELECT 'drehleiter', 'Drehleiter', 3
    UNION ALL SELECT 'pkw', 'PKW', 4
    UNION ALL SELECT 'anhaenger', 'Anhänger', 5
    UNION ALL SELECT 'fuehrungsfahrzeug', 'Führungsfahrzeug', 6
    UNION ALL SELECT 'sonstiges', 'Sonstiges', 7
) d;
