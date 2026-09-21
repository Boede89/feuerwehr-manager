-- Neues Standard-Design für neu angelegte Benutzerkonten
ALTER TABLE users
    ALTER COLUMN theme SET DEFAULT 'dark';
