-- Orders per table (design D4). The table is called "orders" because ORDER is a reserved word.
-- version is incremented on every change and used for optimistic locking by clients.
CREATE TABLE orders (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_label VARCHAR(10) NOT NULL,
    status      VARCHAR(6)  NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'paid', 'voided')),
    opened_by   BIGINT      NOT NULL REFERENCES staff (id),
    opened_at   TIMESTAMPTZ NOT NULL,
    version     INTEGER     NOT NULL DEFAULT 0,
    voided_by   BIGINT      REFERENCES staff (id),
    voided_at   TIMESTAMPTZ,
    void_reason VARCHAR(140)
);

-- At most one open order per table.
CREATE UNIQUE INDEX orders_open_table_uq ON orders (table_label) WHERE status = 'open';

CREATE INDEX orders_opened_at_idx ON orders (opened_at);

CREATE TABLE order_round (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT      NOT NULL REFERENCES orders (id),
    number   INTEGER     NOT NULL,
    sent_by  BIGINT      NOT NULL REFERENCES staff (id),
    sent_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, number)
);

-- Name, type, category, and price are copied from the menu item when the line is added (spec menu-catalog,
-- "Price snapshot"). Prices are integer Rappen (design D6).
CREATE TABLE order_line (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id            BIGINT       NOT NULL REFERENCES orders (id),
    menu_item_id        BIGINT       NOT NULL REFERENCES menu_item (id) ON DELETE RESTRICT,
    name_snapshot       VARCHAR(80)  NOT NULL,
    type_snapshot       VARCHAR(5)   NOT NULL CHECK (type_snapshot IN ('food', 'drink')),
    category_snapshot   VARCHAR(60)  NOT NULL,
    unit_price_snapshot INTEGER      NOT NULL CHECK (unit_price_snapshot >= 0),
    qty                 INTEGER      NOT NULL CHECK (qty BETWEEN 1 AND 99),
    note                VARCHAR(140),
    status              VARCHAR(9)   NOT NULL DEFAULT 'unsent' CHECK (status IN ('unsent', 'sent', 'cancelled')),
    round_id            BIGINT       REFERENCES order_round (id),
    added_by            BIGINT       NOT NULL REFERENCES staff (id),
    added_at            TIMESTAMPTZ  NOT NULL,
    version             INTEGER      NOT NULL DEFAULT 0,
    cancel_reason       VARCHAR(140),
    cancelled_by        BIGINT       REFERENCES staff (id),
    cancelled_at        TIMESTAMPTZ,
    -- Sent and cancelled lines always belong to a round; unsent lines never do.
    CHECK ((status = 'unsent') = (round_id IS NULL)),
    CHECK ((status = 'cancelled') = (cancel_reason IS NOT NULL))
);

CREATE INDEX order_line_order_idx ON order_line (order_id, id);
