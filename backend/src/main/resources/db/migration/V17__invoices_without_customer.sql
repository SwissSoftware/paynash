-- Invoices for orders without a customer (change invoices-without-customer, design D1). The customer copy exists
-- exactly when a customer exists. Invoices no longer need the customers feature.

ALTER TABLE invoice
    ALTER COLUMN customer_id DROP NOT NULL,
    ALTER COLUMN customer DROP NOT NULL,
    ADD CONSTRAINT invoice_customer_copy_check CHECK ((customer_id IS NULL) = (customer IS NULL));

ALTER TABLE installation_settings DROP CONSTRAINT installation_settings_invoices_need_customers;
