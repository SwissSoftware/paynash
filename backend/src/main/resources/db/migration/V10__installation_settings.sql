-- Installation mode and business details (change add-salon-mode, design D1). Exactly one row, id = 1.
-- The business details are only required in salon mode, so all of them are nullable here and checked by the service.
CREATE TABLE installation_settings (
    id             SMALLINT     PRIMARY KEY CHECK (id = 1),
    mode           VARCHAR(5)   NOT NULL DEFAULT 'event' CHECK (mode IN ('event', 'salon')),
    business_name  VARCHAR(80),
    street         VARCHAR(80),
    postal_code    VARCHAR(10),
    city           VARCHAR(60),
    phone          VARCHAR(30),
    email          VARCHAR(120),
    website        VARCHAR(120),
    iban           VARCHAR(34),
    account_holder VARCHAR(80),
    vat_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Format "CHE-123.456.789 MWST", validated by the service.
    vat_uid        VARCHAR(25),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (NOT vat_enabled OR vat_uid IS NOT NULL)
);

INSERT INTO installation_settings (id) VALUES (1);
