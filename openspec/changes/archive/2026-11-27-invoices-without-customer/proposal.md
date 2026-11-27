# Proposal

## Why

Since `configurable-business-features`, invoices and the sales report can be switched on in any business template, but only together with customers: an invoice needs a customer (`invoice.customer_id NOT NULL`, spec `invoicing`), and `invoices` requires `customers`. A café, a bakery, or a restaurant that wants to hand a guest a receipt for a counter sale or a table order without recording a customer cannot do so, and therefore cannot use the sales report either.

Invoices without a customer must be possible in every template. Swiss VAT law allows a receipt without the recipient's name and address only up to CHF 400 (small-amount receipt). Above that, a customer is required.

## What Changes

- An invoice can be issued for an order **without a customer**, as long as its total is at most **CHF 400.00**. Above that, the system rejects it and asks for a customer. Orders with a customer are invoiced as before, at any amount.
- Receipt and A4 PDF of an invoice without a customer show no recipient block. Everything else (business details, number, date, lines, total, VAT) stays the same.
- `invoices` no longer requires `customers`. `sales-report` still requires `invoices`, because the report is based on invoices.
- The sales report and the Excel export count invoices without a customer and show them as one group "Ohne Kunde" (without customer) in the customer list.
- The invoice list shows invoices without a customer with an empty customer column. Search by invoice number keeps working.
- In the app, while invoices are on, every order can be sent and invoiced in one step, with or without a customer, and the order history offers "Issue invoice" for orders without a customer too.
- The business templates stay as they are (decision by the product owner). Admins switch on `invoices` and `sales-report` where needed, also without `customers`.

Not in scope:
- Swiss QR bills and payment slips.
- Collecting a customer on the fly at the moment the CHF 400 limit is hit (the app shows the message, staff pick or create the customer and invoice again).
- Changing the CHF 400 limit per installation.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `business-features`: The dependency `invoices` needs `customers` is removed. `sales-report` still needs `invoices`.
- `invoicing`: Invoices for orders without a customer up to CHF 400.00; invoice content without recipient; confirm and invoice in one step without a customer.
- `sales-reporting`: Revenue per customer includes the group "Ohne Kunde".
- `order-taking`: The order history shows the invoice status of every order while invoices are on, not only of orders with a customer.

## Impact

- Backend: Flyway V17 (`invoice.customer_id` and `invoice.customer` nullable, dependency check `installation_settings_invoices_need_customers` dropped), `Feature` (dependency), `SettingsService`, `InvoiceService` (CHF 400 rule), `Invoice`/`InvoiceRepository` (optional customer), `ReceiptRenderer`, `InvoicePdf`, `ReportService` and `SalesReportExcel` (outer join, group "Ohne Kunde"), `OrderService`/`OrderRepository` (invoice reference for every order).
- App: invoice models with optional customer, invoice and invoice list screens, order screen (send and invoice without customer), order detail screen (issue invoice without customer), report screen, settings screen dependency hint.
- Tests: invoicing, report, settings, and upgrade tests in the backend; widget tests and fake backend in the app.
- Docs: `docs/counter-setup.md`, `docs/event-setup.md`, `docs/salon-setup.md`, `docs/printing.md` (receipts without customer).
