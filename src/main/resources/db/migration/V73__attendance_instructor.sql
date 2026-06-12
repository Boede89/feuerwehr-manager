ALTER TABLE attendance_reports
    ADD COLUMN instructor_responsible VARCHAR(255) NULL AFTER location;
