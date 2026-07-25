ALTER TABLE unit_reservierungen_settings
    ADD COLUMN vehicle_sort_order_json TEXT NULL AFTER vehicle_sort_mode;
