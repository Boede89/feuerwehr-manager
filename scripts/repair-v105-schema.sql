-- Idempotente Schema-Reparatur für V105 (reservation test_data).

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vehicle_reservations'
      AND column_name = 'test_data'
);
SET @col_sql := IF(
    @col_exists = 0,
    'ALTER TABLE vehicle_reservations ADD COLUMN test_data TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'room_reservations'
      AND column_name = 'test_data'
);
SET @col_sql := IF(
    @col_exists = 0,
    'ALTER TABLE room_reservations ADD COLUMN test_data TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'vehicle_reservations'
      AND index_name = 'idx_vehicle_reservations_test_data'
);
SET @idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_vehicle_reservations_test_data ON vehicle_reservations (test_data)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'room_reservations'
      AND index_name = 'idx_room_reservations_test_data'
);
SET @idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_room_reservations_test_data ON room_reservations (test_data)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
