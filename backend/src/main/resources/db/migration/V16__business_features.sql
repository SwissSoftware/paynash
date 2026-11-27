-- Switchable features, business templates, and configurable terms replace the installation mode
-- (change configurable-business-features, design D1, D3, D8).

ALTER TABLE installation_settings
    ADD COLUMN feature_tables       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_stations     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_customers    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_invoices     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN feature_sales_report BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN template             VARCHAR(10) NOT NULL DEFAULT 'custom'
        CHECK (template IN ('event', 'restaurant', 'salon', 'counter', 'custom')),
    ADD COLUMN term_waiter          VARCHAR(30) NOT NULL DEFAULT 'Waiter' CHECK (length(trim(term_waiter)) > 0),
    ADD COLUMN term_items           VARCHAR(30) NOT NULL DEFAULT 'Menu' CHECK (length(trim(term_items)) > 0),
    ADD COLUMN term_send            VARCHAR(30) NOT NULL DEFAULT 'Send' CHECK (length(trim(term_send)) > 0);

-- Existing installations keep their behaviour (spec business-features).
UPDATE installation_settings
SET template = 'event', feature_tables = TRUE, feature_stations = TRUE
WHERE mode = 'event';

UPDATE installation_settings
SET template = 'salon', feature_customers = TRUE, feature_invoices = TRUE, feature_sales_report = TRUE,
    term_waiter = 'Staff', term_items = 'Services', term_send = 'Confirm'
WHERE mode = 'salon';

-- Invoices need customers, and the sales report is based on invoices (spec business-features, "Feature dependencies").
ALTER TABLE installation_settings
    ADD CONSTRAINT installation_settings_invoices_need_customers CHECK (NOT feature_invoices OR feature_customers),
    ADD CONSTRAINT installation_settings_report_needs_invoices CHECK (NOT feature_sales_report OR feature_invoices),
    DROP COLUMN mode;

-- An order may have a table, a customer, both, or neither. The rules of the enabled features are checked when the
-- order is sent, so orders saved under earlier settings stay valid (design D3).
ALTER TABLE orders DROP CONSTRAINT orders_table_or_customer_check;
