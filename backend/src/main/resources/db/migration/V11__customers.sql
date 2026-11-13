-- Salon customers (change add-salon-mode, spec customer-management, design D4).
-- The customer number comes from its own sequence, so it is never given again, also after a customer is deleted.
CREATE SEQUENCE customer_number_seq;

CREATE TABLE customer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number        INTEGER      NOT NULL DEFAULT nextval('customer_number_seq'),
    first_name    VARCHAR(60),
    last_name     VARCHAR(60),
    phone         VARCHAR(30),
    -- Only the digits of phone, for searching regardless of spaces and separators.
    phone_digits  VARCHAR(30),
    email         VARCHAR(120),
    street        VARCHAR(80),
    postal_code   VARCHAR(10),
    city          VARCHAR(60),
    note          VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Set when the personal data was removed on request; the record stays for reports.
    anonymized_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (anonymized_at IS NOT NULL OR first_name IS NOT NULL OR last_name IS NOT NULL)
);

ALTER SEQUENCE customer_number_seq OWNED BY customer.number;

CREATE UNIQUE INDEX customer_number_uq ON customer (number);
CREATE INDEX customer_last_name_idx ON customer (lower(last_name));
