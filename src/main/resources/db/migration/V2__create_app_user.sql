-- Users: the secondary collection for registration and authentication. ("user" is reserved in PostgreSQL.)
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL CHECK (username ~ '^[A-Za-z0-9_.-]{3,50}$'),
    email         VARCHAR(254) NOT NULL CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),   -- 254: RFC 5321 path limit; sanity only, real validation in the domain
    password_hash VARCHAR(100) NOT NULL,                              -- BCrypt, never plaintext
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- requirement: username and email are unique REGARDLESS OF CASE (AshKetchum = ashketchum, Ash@Pallet.town = ash@PALLET.TOWN)
CREATE UNIQUE INDEX uq_app_user_username ON app_user (lower(username));
CREATE UNIQUE INDEX uq_app_user_email    ON app_user (lower(email));
