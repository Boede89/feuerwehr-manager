-- Anmeldung per Benutzername oder E-Mail; Legacy-Rolle bereinigen

UPDATE users SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';

ALTER TABLE users
    ADD COLUMN login_email VARCHAR(255) NULL AFTER username;

CREATE UNIQUE INDEX uk_users_login_email ON users (login_email);
