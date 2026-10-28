CREATE TABLE staff (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(40)  NOT NULL,
    display_name    VARCHAR(80)  NOT NULL,
    role            VARCHAR(10)  NOT NULL CHECK (role IN ('admin', 'waiter')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    pin_hash        VARCHAR(255) NOT NULL,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Usernames are unique regardless of case.
CREATE UNIQUE INDEX staff_username_uq ON staff (lower(username));

-- Only a SHA-256 hash of the bearer token is stored, so a DB dump does not leak live sessions.
CREATE TABLE session (
    token_hash  CHAR(64)    PRIMARY KEY,
    staff_id    BIGINT      NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX session_staff_idx ON session (staff_id);
