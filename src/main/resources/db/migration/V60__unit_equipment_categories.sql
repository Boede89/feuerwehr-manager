-- Gerätekategorien einheitsweit statt pro Fahrzeug

CREATE TABLE unit_equipment_categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id    BIGINT       NOT NULL,
    name       VARCHAR(255) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_uec_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_uec_unit_name UNIQUE (unit_id, name)
);

INSERT INTO unit_equipment_categories (unit_id, name, sort_order)
SELECT v.unit_id, MIN(vec.name), MIN(vec.sort_order)
FROM vehicle_equipment_categories vec
         JOIN vehicles v ON v.id = vec.vehicle_id
GROUP BY v.unit_id, LOWER(vec.name);

UPDATE vehicle_equipment ve
    JOIN vehicle_equipment_categories vec ON ve.category_id = vec.id
    JOIN vehicles v ON vec.vehicle_id = v.id
    JOIN unit_equipment_categories uec ON uec.unit_id = v.unit_id AND LOWER(uec.name) = LOWER(vec.name)
SET ve.category_id = uec.id
WHERE ve.category_id IS NOT NULL;

ALTER TABLE vehicle_equipment
    DROP FOREIGN KEY fk_ve_category;

DROP TABLE vehicle_equipment_categories;

ALTER TABLE vehicle_equipment
    ADD CONSTRAINT fk_ve_uec_category
        FOREIGN KEY (category_id) REFERENCES unit_equipment_categories (id) ON DELETE SET NULL;

CREATE INDEX idx_uec_unit ON unit_equipment_categories (unit_id);
