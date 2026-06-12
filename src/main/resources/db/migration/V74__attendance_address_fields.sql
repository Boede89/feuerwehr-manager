ALTER TABLE attendance_reports
    ADD COLUMN postal_code VARCHAR(10) NULL AFTER location,
    ADD COLUMN district VARCHAR(128) NULL AFTER postal_code,
    ADD COLUMN street VARCHAR(255) NULL AFTER district,
    ADD COLUMN house_number VARCHAR(20) NULL AFTER street,
    ADD COLUMN objekt VARCHAR(255) NULL AFTER house_number;
