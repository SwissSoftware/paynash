-- Ticket printing (spec ticket-printing, design D3).

-- The Epson TM-m30III printers. Each item type is served by at most one active printer; when none is active the
-- ticket fails with "no printer configured".
CREATE TABLE printer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL,
    address    VARCHAR(255) NOT NULL,
    device_id  VARCHAR(60)  NOT NULL DEFAULT 'local_printer',
    item_type  VARCHAR(5)   NOT NULL CHECK (item_type IN ('food', 'drink')),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX printer_name_uq ON printer (lower(name));
CREATE UNIQUE INDEX printer_active_type_uq ON printer (item_type) WHERE active;

-- The outbox: one row per ticket, written in the same transaction as the order change. content is the rendered
-- ticket, so a reprint prints exactly what was printed before. printer_id is null when no printer was configured.
CREATE TABLE print_job (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         BIGINT       NOT NULL REFERENCES orders (id),
    item_type        VARCHAR(5)   NOT NULL CHECK (item_type IN ('food', 'drink')),
    printer_id       BIGINT       REFERENCES printer (id),
    kind             VARCHAR(7)   NOT NULL CHECK (kind IN ('normal', 'storno', 'reprint')),
    content          TEXT         NOT NULL,
    status           VARCHAR(7)   NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'printed', 'failed')),
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(200),
    first_attempt_at TIMESTAMPTZ,
    next_attempt_at  TIMESTAMPTZ,
    printed_at       TIMESTAMPTZ,
    reprint_of       BIGINT       REFERENCES print_job (id),
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX print_job_pending_idx ON print_job (printer_id, id) WHERE status = 'pending';
CREATE INDEX print_job_order_idx ON print_job (order_id);
