-- Services and salon orders (change add-salon-mode, design D3, D6).

-- Item type 'service' with an optional duration, and a VAT rate per item in tenths of a percent (81 = 8.1 %).
-- Which rates are allowed is checked by the service, so a legal change needs no migration.
ALTER TABLE menu_item
    ALTER COLUMN type TYPE VARCHAR(7),
    DROP CONSTRAINT menu_item_type_check,
    ADD CONSTRAINT menu_item_type_check CHECK (type IN ('food', 'drink', 'service')),
    ADD COLUMN duration_min INTEGER CHECK (duration_min BETWEEN 5 AND 600),
    ADD COLUMN vat_rate_permille INTEGER NOT NULL DEFAULT 81 CHECK (vat_rate_permille BETWEEN 0 AND 999),
    ADD CONSTRAINT menu_item_duration_service_check CHECK (duration_min IS NULL OR type = 'service');

-- Lines keep the VAT rate that was valid when they were added. Existing lines get 8.1 %; event mode never shows it.
ALTER TABLE order_line
    ALTER COLUMN type_snapshot TYPE VARCHAR(7),
    DROP CONSTRAINT order_line_type_snapshot_check,
    ADD CONSTRAINT order_line_type_snapshot_check CHECK (type_snapshot IN ('food', 'drink', 'service')),
    ADD COLUMN vat_rate_snapshot INTEGER NOT NULL DEFAULT 81 CHECK (vat_rate_snapshot BETWEEN 0 AND 999);

-- An order belongs to a table (event mode) or to a customer (salon mode), never both or neither.
ALTER TABLE orders
    ALTER COLUMN table_label DROP NOT NULL,
    ADD COLUMN customer_id BIGINT REFERENCES customer (id),
    ADD CONSTRAINT orders_table_or_customer_check CHECK ((table_label IS NULL) <> (customer_id IS NULL));

CREATE INDEX orders_customer_idx ON orders (customer_id) WHERE customer_id IS NOT NULL;
