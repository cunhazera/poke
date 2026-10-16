-- The basic user model the task API assumes (no authentication yet).
CREATE TABLE app_user (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name TEXT NOT NULL CHECK (btrim(full_name) <> ''),
    username  TEXT NOT NULL CHECK (username ~ '^[a-z0-9._-]{3,50}$'),
    email     TEXT NOT NULL CHECK (email ~ '^[^@\s]+@[^@\s]+$')
);

CREATE UNIQUE INDEX uq_app_user_username ON app_user (lower(username));
CREATE UNIQUE INDEX uq_app_user_email    ON app_user (lower(email));
