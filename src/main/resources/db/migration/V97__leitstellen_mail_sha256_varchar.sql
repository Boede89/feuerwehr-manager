-- Hibernate validate erwartet VARCHAR, Flyway V94 hatte CHAR(64)
ALTER TABLE leitstellen_mail_imports
    MODIFY COLUMN attachment_sha256 VARCHAR(64) NOT NULL;
