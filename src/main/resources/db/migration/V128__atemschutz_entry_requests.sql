-- Anträge auf Atemschutz-Nachweise (Self-Service → Ausbilder-Freigabe)
CREATE TABLE atemschutz_entry_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    event_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    review_note VARCHAR(1000) NULL,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_atemschutz_entry_req_unit FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_atemschutz_entry_req_requester FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_atemschutz_entry_req_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_atemschutz_entry_req_unit_status ON atemschutz_entry_requests (unit_id, status, test_data);

CREATE TABLE atemschutz_entry_request_carriers (
    request_id BIGINT NOT NULL,
    carrier_id BIGINT NOT NULL,
    PRIMARY KEY (request_id, carrier_id),
    CONSTRAINT fk_atemschutz_entry_req_carrier_req FOREIGN KEY (request_id) REFERENCES atemschutz_entry_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_atemschutz_entry_req_carrier FOREIGN KEY (carrier_id) REFERENCES atemschutz_carriers (id)
);
