-- Stored responses of mutating requests, replayed for a repeated Idempotency-Key (design D5).
-- response_status is NULL while the first request is still being processed.
CREATE TABLE idempotency_key (
    key              VARCHAR(100) NOT NULL,
    staff_id         BIGINT       NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    fingerprint      CHAR(64)     NOT NULL,
    response_status  INTEGER,
    response_type    VARCHAR(100),
    response_body    BYTEA,
    created_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (staff_id, key)
);

CREATE INDEX idempotency_key_created_idx ON idempotency_key (created_at);
