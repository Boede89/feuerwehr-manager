CREATE TABLE vehicle_checklist_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    interval_type VARCHAR(20) NOT NULL DEFAULT 'manuell',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vct_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_vct_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE vehicle_checklist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    position INT NOT NULL DEFAULT 0,
    label VARCHAR(200) NOT NULL,
    CONSTRAINT fk_vci_template FOREIGN KEY (template_id) REFERENCES vehicle_checklist_templates(id) ON DELETE CASCADE
);

CREATE TABLE vehicle_checklists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    filled_by BIGINT NULL,
    filled_name VARCHAR(200) NULL,
    notes TEXT NULL,
    filled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vc_template FOREIGN KEY (template_id) REFERENCES vehicle_checklist_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_user FOREIGN KEY (filled_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE vehicle_checklist_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checklist_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_label VARCHAR(200) NOT NULL,
    result VARCHAR(20) NOT NULL DEFAULT 'nicht_geprueft',
    note VARCHAR(300) NULL,
    CONSTRAINT fk_vce_checklist FOREIGN KEY (checklist_id) REFERENCES vehicle_checklists(id) ON DELETE CASCADE,
    CONSTRAINT fk_vce_item FOREIGN KEY (item_id) REFERENCES vehicle_checklist_items(id) ON DELETE CASCADE
);

CREATE INDEX idx_vct_vehicle ON vehicle_checklist_templates(vehicle_id);
CREATE INDEX idx_vci_template ON vehicle_checklist_items(template_id);
CREATE INDEX idx_vc_vehicle ON vehicle_checklists(vehicle_id);
CREATE INDEX idx_vc_template ON vehicle_checklists(template_id);
CREATE INDEX idx_vc_filled_at ON vehicle_checklists(filled_at);
CREATE INDEX idx_vce_checklist ON vehicle_checklist_entries(checklist_id);
