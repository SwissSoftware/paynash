-- Invoices and payments (change add-salon-mode, spec invoicing, design D5-D7).
-- An invoice is a complete copy made when it is issued: receipt, PDF, report, and export read only these tables.

CREATE TABLE invoice (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- "YYYY-NNNN", gapless per year through invoice_counter.
    number        VARCHAR(12)  NOT NULL,
    year          SMALLINT     NOT NULL,
    seq           INTEGER      NOT NULL CHECK (seq >= 1),
    order_id      BIGINT       NOT NULL REFERENCES orders (id),
    customer_id   BIGINT       NOT NULL REFERENCES customer (id),
    -- Date in Europe/Zurich; revenue is counted on this date.
    invoice_date  DATE         NOT NULL,
    issued_at     TIMESTAMPTZ  NOT NULL,
    issued_by     BIGINT       NOT NULL REFERENCES staff (id),
    -- Copies of the business details and the customer at issue time.
    business      JSONB        NOT NULL,
    customer      JSONB        NOT NULL,
    vat_enabled   BOOLEAN      NOT NULL,
    vat_uid       VARCHAR(25),
    total_rappen  BIGINT       NOT NULL CHECK (total_rappen >= 0),
    status        VARCHAR(9)   NOT NULL DEFAULT 'issued' CHECK (status IN ('issued', 'cancelled')),
    cancelled_at  TIMESTAMPTZ,
    cancelled_by  BIGINT       REFERENCES staff (id),
    cancel_reason VARCHAR(140),
    UNIQUE (year, seq),
    CHECK ((status = 'cancelled') = (cancel_reason IS NOT NULL)),
    CHECK (NOT vat_enabled OR vat_uid IS NOT NULL)
);

CREATE UNIQUE INDEX invoice_number_uq ON invoice (number);
-- At most one invoice per order that is not cancelled.
CREATE UNIQUE INDEX invoice_order_active_uq ON invoice (order_id) WHERE status = 'issued';
CREATE INDEX invoice_date_idx ON invoice (invoice_date);
CREATE INDEX invoice_customer_idx ON invoice (customer_id);

-- The order's lines that were not cancelled when the invoice was issued.
CREATE TABLE invoice_line (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id        BIGINT       NOT NULL REFERENCES invoice (id),
    position          INTEGER      NOT NULL,
    order_line_id     BIGINT       NOT NULL REFERENCES order_line (id),
    name              VARCHAR(80)  NOT NULL,
    category          VARCHAR(60)  NOT NULL,
    qty               INTEGER      NOT NULL CHECK (qty BETWEEN 1 AND 99),
    unit_price_rappen INTEGER      NOT NULL CHECK (unit_price_rappen >= 0),
    vat_rate_permille INTEGER      NOT NULL,
    amount_rappen     BIGINT       NOT NULL CHECK (amount_rappen >= 0),
    note              VARCHAR(140),
    UNIQUE (invoice_id, position)
);

-- VAT per rate, computed once at issue time (design D6). Empty when VAT was off.
CREATE TABLE invoice_vat (
    invoice_id        BIGINT  NOT NULL REFERENCES invoice (id),
    vat_rate_permille INTEGER NOT NULL,
    gross_rappen      BIGINT  NOT NULL,
    net_rappen        BIGINT  NOT NULL,
    vat_rappen        BIGINT  NOT NULL,
    PRIMARY KEY (invoice_id, vat_rate_permille),
    CHECK (gross_rappen = net_rappen + vat_rappen)
);

-- Gapless numbering: the year's row is locked and incremented in the issuing transaction.
CREATE TABLE invoice_counter (
    year     SMALLINT PRIMARY KEY,
    last_seq INTEGER  NOT NULL CHECK (last_seq >= 0)
);

-- Payments are never deleted; undoing one sets the removal fields (design D7).
CREATE TABLE invoice_payment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id    BIGINT       NOT NULL REFERENCES invoice (id),
    method        VARCHAR(13)  NOT NULL CHECK (method IN ('cash', 'card', 'twint', 'bank_transfer')),
    paid_on       DATE         NOT NULL,
    recorded_by   BIGINT       NOT NULL REFERENCES staff (id),
    recorded_at   TIMESTAMPTZ  NOT NULL,
    removed_at    TIMESTAMPTZ,
    removed_by    BIGINT       REFERENCES staff (id),
    remove_reason VARCHAR(140),
    CHECK ((removed_at IS NULL) = (remove_reason IS NULL)),
    CHECK ((removed_at IS NULL) = (removed_by IS NULL))
);

-- At most one active payment per invoice; the invoice is paid exactly when one exists.
CREATE UNIQUE INDEX invoice_payment_active_uq ON invoice_payment (invoice_id) WHERE removed_at IS NULL;
