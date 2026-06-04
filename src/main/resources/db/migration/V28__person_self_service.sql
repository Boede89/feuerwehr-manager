ALTER TABLE persons
    ADD COLUMN email_private VARCHAR(255) NULL AFTER email,
    ADD COLUMN address VARCHAR(200) NULL AFTER phone;

CREATE TABLE person_emergency_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    relationship VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_person_emergency_contacts_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    INDEX idx_person_emergency_contacts_person (person_id, sort_order)
);
