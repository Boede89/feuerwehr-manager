CREATE TABLE unit_print_settings (
    unit_id BIGINT NOT NULL,
    print_mode VARCHAR(16) NOT NULL DEFAULT 'DIALOG',
    cups_printer_name VARCHAR(128) NULL,
    cups_server VARCHAR(128) NULL,
    cups_use_postscript BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (unit_id),
    CONSTRAINT fk_unit_print_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

INSERT INTO unit_print_settings (unit_id, print_mode, cups_use_postscript)
SELECT id, 'DIALOG', FALSE FROM units;
