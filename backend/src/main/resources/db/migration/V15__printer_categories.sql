-- Printers print the categories assigned to them, and receipts if switched on (change printer-category-routing,
-- design D1, D2, D6). Replaces the fixed purpose food / drink / receipt.

ALTER TABLE printer
    ADD COLUMN prints_tickets  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN prints_receipts BOOLEAN NOT NULL DEFAULT FALSE;

-- A category may go to several printers. Deleting a category or printer drops its assignments.
CREATE TABLE printer_category (
    printer_id  BIGINT NOT NULL REFERENCES printer (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    PRIMARY KEY (printer_id, category_id)
);

CREATE INDEX printer_category_category_idx ON printer_category (category_id);

-- The printers a sent line was routed to, so its STORNO goes to the same printers even after the assignment changed.
CREATE TABLE order_line_printer (
    order_line_id BIGINT NOT NULL REFERENCES order_line (id) ON DELETE CASCADE,
    printer_id    BIGINT NOT NULL REFERENCES printer (id),
    PRIMARY KEY (order_line_id, printer_id)
);

-- Today's behaviour, expressed as assignments: every category with items of a type goes to that type's printer.
UPDATE printer SET prints_tickets = TRUE WHERE purpose IN ('food', 'drink');
UPDATE printer SET prints_receipts = TRUE WHERE purpose = 'receipt';

INSERT INTO printer_category (printer_id, category_id)
SELECT DISTINCT p.id, mi.category_id
FROM printer p JOIN menu_item mi ON mi.type = p.purpose
WHERE p.purpose IN ('food', 'drink');

DO $$
DECLARE
    mixed RECORD;
BEGIN
    FOR mixed IN
        SELECT c.name FROM category c JOIN menu_item mi ON mi.category_id = c.id
        WHERE mi.type IN ('food', 'drink')
        GROUP BY c.id, c.name HAVING count(DISTINCT mi.type) > 1
    LOOP
        RAISE NOTICE 'Category "%" has food and drink items and now prints on both printers. Check its assignment.',
            mixed.name;
    END LOOP;
END $$;

-- Lines already sent: the printers of their original tickets.
INSERT INTO order_line_printer (order_line_id, printer_id)
SELECT DISTINCT l.id, j.printer_id
FROM order_line l
JOIN print_job j ON j.order_id = l.order_id AND j.kind = 'normal' AND j.item_type = l.type_snapshot
WHERE l.status <> 'unsent' AND j.printer_id IS NOT NULL;

DROP INDEX printer_active_purpose_uq;
ALTER TABLE printer DROP COLUMN purpose;

-- Whether a job is a ticket or a receipt follows from order_id / invoice_id (print_job_source_check).
ALTER TABLE print_job
    DROP CONSTRAINT print_job_receipt_check,
    DROP COLUMN item_type;
