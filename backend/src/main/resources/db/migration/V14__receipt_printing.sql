-- Invoice receipts through the print outbox (change add-salon-mode, design D8).

-- A printer serves an item type (food, drink) or receipts. One active printer per purpose, as before.
ALTER TABLE printer RENAME COLUMN item_type TO purpose;
ALTER TABLE printer
    ALTER COLUMN purpose TYPE VARCHAR(7),
    DROP CONSTRAINT printer_item_type_check,
    ADD CONSTRAINT printer_purpose_check CHECK (purpose IN ('food', 'drink', 'receipt'));
ALTER INDEX printer_active_type_uq RENAME TO printer_active_purpose_uq;

-- A job prints either an order's ticket or an invoice's receipt. 'copy' is a receipt printed again ("KOPIE").
ALTER TABLE print_job
    ALTER COLUMN item_type TYPE VARCHAR(7),
    DROP CONSTRAINT print_job_item_type_check,
    ADD CONSTRAINT print_job_item_type_check CHECK (item_type IN ('food', 'drink', 'receipt')),
    DROP CONSTRAINT print_job_kind_check,
    ADD CONSTRAINT print_job_kind_check CHECK (kind IN ('normal', 'storno', 'reprint', 'copy')),
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN invoice_id BIGINT REFERENCES invoice (id),
    ADD CONSTRAINT print_job_source_check CHECK ((order_id IS NULL) <> (invoice_id IS NULL)),
    ADD CONSTRAINT print_job_receipt_check CHECK ((item_type = 'receipt') = (invoice_id IS NOT NULL));

CREATE INDEX print_job_invoice_idx ON print_job (invoice_id) WHERE invoice_id IS NOT NULL;
