-- Routing-Felder für manuelle Einsätze / Alarmdepesche

ALTER TABLE manual_alarms
    ADD COLUMN route_start_address VARCHAR(512) NULL AFTER route_info,
    ADD COLUMN route_distance_m INT NULL AFTER route_start_address,
    ADD COLUMN route_duration_sec INT NULL AFTER route_distance_m,
    ADD COLUMN route_avg_speed_kmh DECIMAL(6, 1) NULL AFTER route_duration_sec,
    ADD COLUMN route_steps_json TEXT NULL AFTER route_avg_speed_kmh,
    ADD COLUMN route_title VARCHAR(255) NULL AFTER route_steps_json;
