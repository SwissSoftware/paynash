-- Orders are built on the device and sent in one go; a sent order is final (design D4, revised). There are no open
-- orders per table and no rounds anymore. Only development data exists, so existing orders are removed instead of
-- converted.
DELETE FROM idempotency_key;
DELETE FROM order_line;
DELETE FROM orders;

DROP INDEX orders_open_table_uq;

ALTER TABLE orders RENAME COLUMN opened_by TO sent_by;
ALTER TABLE orders RENAME COLUMN opened_at TO sent_at;
ALTER INDEX orders_opened_at_idx RENAME TO orders_sent_at_idx;
ALTER TABLE orders
    ALTER COLUMN status SET DEFAULT 'sent',
    DROP CONSTRAINT orders_status_check,
    ADD CONSTRAINT orders_status_check CHECK (status IN ('sent', 'voided'));

-- Dropping round_id also drops the check that tied unsent lines to rounds. Lines are created together with their
-- order, so the order's sent_by/sent_at replace added_by/added_at.
ALTER TABLE order_line
    DROP COLUMN round_id,
    DROP COLUMN version,
    DROP COLUMN added_by,
    DROP COLUMN added_at,
    ALTER COLUMN status SET DEFAULT 'sent',
    DROP CONSTRAINT order_line_status_check,
    ADD CONSTRAINT order_line_status_check CHECK (status IN ('sent', 'cancelled'));

DROP TABLE order_round;
