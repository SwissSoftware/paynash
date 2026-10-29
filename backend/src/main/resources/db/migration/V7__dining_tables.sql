-- The restaurant's tables, a lookup list for waiters (change add-table-management, design D1).
-- Orders keep their table as text in orders.table_label, so nothing references this table.
-- Called dining_table because TABLE is a reserved word.
CREATE TABLE dining_table (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number     VARCHAR(10) NOT NULL,
    name       VARCHAR(60),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Numbers are stored upper-case; the index also guards against other casings.
CREATE UNIQUE INDEX dining_table_number_uq ON dining_table (upper(number));
