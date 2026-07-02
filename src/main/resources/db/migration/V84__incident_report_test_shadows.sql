-- Testmodus: Schattenkopien von Produktiv-Einsatzberichten

ALTER TABLE incident_reports
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_ir_production_source FOREIGN KEY (production_source_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_ir_production_shadow (production_source_id);
