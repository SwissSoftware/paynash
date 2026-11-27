# Design

## Context

See proposal.md for motivation and specs/ for the requirements. This design builds on the stack and patterns of the in-flight changes:

- Java/Spring Boot backend with JDBC and Flyway (migrations V1 to V9), PostgreSQL, one Flutter app for web, Android, and iOS (`add-pos-ordering-system` D1).
- On-site server in the LAN of the venue (D2). For a salon this is a small machine at the counter, the same as at an event.
- Money as integer Rappen (D6), `Idempotency-Key` header for writes that must not repeat (D5), Europe/Zurich for all date grouping (D9).
- Printing through the `print_job` outbox and a worker per printer, with the job states `pending -> printing -> printed | unclear | failed` (`harden-printing-for-events` D1 to D3).
- `orders` has a required `table_label`, `order_line` has snapshots for name, type, category, and price, and `printer.item_type` and `print_job.item_type` allow only `food` and `drink`.

## Goals / Non-Goals

**Goals:**
- One codebase and one database schema for both modes. Salon features reuse orders, lines, cancel/void, idempotency, and printing instead of copying them.
- Invoices that are legally sound for a Swiss small business: final once issued, gapless numbers, kept for 10 years, the same content on receipt, PDF, and in Excel.
- Report totals that are identical on the devices and in the Excel file.

**Non-Goals:**
- A tenant model. One installation still serves exactly one venue.
- Changing event behavior. Event mode works the same, apart from the new database columns.
- A general document template engine. Receipt and PDF layouts are fixed in code.

## Decisions

### D1: Settings as one row
A table `installation_settings` with exactly one row (`id = 1`, checked in the database) holds `mode`, the business details, `vat_enabled`, and `vat_uid`. `GET /settings` is readable by every logged-in user. The app loads it after login and on each app resume, and builds its navigation from `mode`. `PUT /admin/settings` (details and VAT) and `PUT /admin/settings/mode` are admin-only.
- Changing the mode runs in one transaction with `SELECT ... FOR UPDATE` on the settings row and a check that `orders` is empty. This makes the "mode change only without orders" rule safe against an order arriving at the same moment.
- *Alternative*: the mode as an environment variable of the deployment. This was rejected because the admin should set up a salon from the app, and because the check against existing orders needs the database anyway.

### D2: Mode gate on the server
The backend checks the mode on the server, not only in the app. Controllers for customers, invoices, and reports, and the table and station endpoints, carry a `@RequiresMode(SALON)` or `@RequiresMode(EVENT)` annotation. An interceptor checks it against the cached settings, which are refreshed when they are saved. A wrong mode returns `409 Conflict` with the code `wrong_mode`.

### D3: Salon orders reuse `orders`
`orders.table_label` becomes nullable, and `orders.customer_id` (FK to `customer`) is added. A check constraint requires exactly one of them. `order_line` gets `vat_rate_snapshot`. The type checks on `menu_item.type` and `order_line.type_snapshot` are extended with `service`. "Confirm" in the app calls the existing send endpoint. In salon mode the service requires `customer_id` and only services, and rejects `table_label`.
- *Why*: cancel, void, idempotency, order history, and the 5-second refresh work without new code. The order status stays `sent`. Only the app shows it as "confirmed".
- *Alternative*: separate `sale` and `sale_line` tables. This was rejected because it duplicates the order lifecycle and its tests.
- The rule "no cancel or void while an invoice that is not cancelled exists" is checked in `OrderService` with a row lock on the order, the same lock that issuing an invoice takes (D5).

### D4: Customers
Table `customer` with `id`, `number` (from its own sequence, unique, never reused), first and last name, phone, `phone_digits` (only digits, for search), email, street, postal code, city, note, `active`, `anonymized_at`, and timestamps.
- Search uses `lower(...) LIKE` on names, email, and number, and `phone_digits LIKE` on the phone. A salon has a few thousand customers at most, so a B-tree index on `lower(last_name)` is enough. No `pg_trgm`.
- The duplicate warning is a separate `GET /customers/duplicates?...` call that the app makes before creating. The server never blocks a create because of a possible duplicate.
- **Delete or anonymize**: without orders, a `DELETE`. With orders, the personal fields are set to null, `anonymized_at` is set, and `active` is set to false, all in one transaction. Invoices keep their own copy (D5), so retention is not affected.
- **Data export**: `GET /customers/{id}/export` returns a JSON file with the record, the invoices, their lines, and the payments. JSON is enough for an access request and easy to produce.

### D5: Invoices as a snapshot
Tables:
- `invoice`: `id`, `number` (text `YYYY-NNNN`, unique), `year`, `seq`, `order_id`, `invoice_date` (date in Europe/Zurich), `issued_at`, `issued_by`, `business` (JSONB copy of the settings at issue time), `customer` (JSONB copy of the customer), `vat_enabled`, `total_rappen`, `status` (`issued` or `cancelled`), and the cancel fields (`cancelled_at`, `cancelled_by`, `cancel_reason`).
- `invoice_line`: a copy of each line that is not cancelled, with qty, name, unit price, VAT rate, and amount.
- `invoice_vat`: one row per VAT rate with gross, net, and VAT in Rappen, computed once at issue time.
- A partial unique index on `invoice(order_id) WHERE status = 'issued'` ensures at most one active invoice per order.

The invoice is fully stored at issue time. Receipt, PDF, report, and export read only these tables, never the current settings, customer, or menu. This makes "invoices are final" a property of the data, not of the code.

**Gapless numbering**: table `invoice_counter(year PK, last_seq)`. Issuing takes `SELECT ... FOR UPDATE` on the year's row (inserting it if it is missing), increments it, and inserts the invoice in the same transaction. A rollback also rolls back the counter, so there are no gaps. Concurrent issuing is serialized per year, which is fine at salon volume.
- *Alternative*: a PostgreSQL sequence. This was rejected because sequences leave gaps on rollback.

Issuing uses the `Idempotency-Key` mechanism (D5 of `add-pos-ordering-system`). "Confirm and invoice" is two calls from the app, each with its own key: confirm, then issue. This matches the spec: the order stays confirmed if issuing fails, and the app shows why.

### D6: VAT calculation
`menu_item.vat_rate_permille` and `order_line.vat_rate_snapshot` are integers in tenths of a percent (81 = 8.1 %). Allowed values are 0, 26, 38, and 81, checked in the service so that a future legal change needs only code, no migration. Prices include VAT. For each rate on an invoice: `vat = round_half_up(gross_rappen * rate / (1000 + rate))`, `net = gross - vat`. The VAT is computed per rate on the sum of lines, not per line, as the Federal Tax Administration (ESTV) allows. When VAT is off, `invoice_vat` stays empty and `vat_enabled = false`.

### D7: Payments as rows
Table `invoice_payment`: `invoice_id`, `method`, `paid_on`, `recorded_by`, `recorded_at`, and `removed_at`, `removed_by`, `remove_reason`. A partial unique index on `invoice_id WHERE removed_at IS NULL` allows at most one active payment. The invoice is `paid` exactly when an active payment exists. "Undo payment" sets the removal fields and never deletes, so the history stays.
- Endpoints: `POST /invoices/{id}/payment` (staff) and `POST /admin/invoices/{id}/payment/undo` with a reason (admin).
- *Alternative*: payment columns on `invoice`. This was rejected because undo would overwrite the record of the payment that was removed.

### D8: Receipts through the print outbox
- `printer.item_type` is renamed to `purpose`, and its check allows `food`, `drink`, and `receipt`. In code, a `PrintPurpose` enum (food, drink, receipt) replaces the menu `ItemType` for printers and print jobs. The existing partial unique index on active printers per purpose already ensures one active receipt printer.
- `print_job` gets `invoice_id` (nullable). `order_id` becomes nullable. A check requires one of them. `print_job.item_type` allows `receipt`, and `kind` allows `copy`.
- Issuing an invoice inserts a receipt job in the same transaction, so a crash cannot lose it. The content is rendered when the job is created, as for tickets, so a copy prints exactly the same text with the "KOPIE" marker added.
- A new `ReceiptRenderer` creates ePOS-Print XML for 48 columns (font A on 80 mm paper), with right-aligned amounts. "STORNIERT" receipts are rendered from the cancelled invoice.
- The worker, waiting, crash recovery, and running ticket number work unchanged. In salon mode there is no station view: unclear and failed receipts are shown on the invoice, and "print copy" is the way to resolve them. Choosing it marks the unclear job as resolved.
- The production ticket routing skips lines with `type_snapshot = 'service'`, so confirming, cancelling, and voiding in salon mode create no jobs.

### D9: PDF with OpenPDF
The A4 PDF is created on demand from the invoice tables with **OpenPDF** (LGPL/MPL, actively maintained, simple table API). It embeds a font (Liberation Sans) so that umlauts and "CHF" look the same on every system. The PDF is not stored: the data is final, so it can be produced again identically at any time. The file is named `Rechnung-<number>.pdf`.
- *Alternatives*: Apache PDFBox (low-level, much more layout code), or HTML to PDF with openhtmltopdf (nice templates, but a heavier dependency and an uncertain maintenance situation).
- Layout: business block at the top left, customer address placed for a C5 window envelope on the right, then title, number and date, the line table, the total, the VAT table, and the bank details in the footer if set.

### D10: Reporting from invoice tables
A `ReportService` builds one `SalesReport` object for a period with SQL aggregations over `invoice` (status `issued`), `invoice_line`, `invoice_vat`, and the active `invoice_payment` rows:
- days through `generate_series(from, to, '1 day')` LEFT JOIN, so days without revenue are included
- services grouped by `name` from `invoice_line`, customers by `invoice.customer->>'number'` and the current customer record for the display name (anonymized customers show "Deleted customer")
- paid and open amounts by whether an active payment exists, and revenue per method over the active payments

`GET /admin/reports/sales?from&to` returns the object as JSON. `GET /admin/reports/sales.xlsx?from&to` writes **the same object** with Apache POI (`XSSFWorkbook`; the data is small, so streaming is not needed). Amounts are written as numeric cells with the format `#,##0.00`, and dates as date cells. The sheets "Rechnungen" and "Positionen" are read separately, including cancelled invoices with their status. Because both endpoints use the same object, the totals match by design.

### D11: App structure
- A `SettingsController` provides the mode, and `home` builds the navigation from it. Salon mode shows: Order (customer picker plus service tiles), Customers, Invoices, Report (admin only), and Admin (services, staff, printers, settings).
- The customer picker is a searchable list with a "New customer" action that opens a short form (name only, more optional) and runs the duplicate check.
- The invoice view shows the content, the receipt status, and the actions "Print copy", "Download PDF", "Mark as paid", "Cancel" (admin), and "Undo payment" (admin).
- PDF and Excel downloads reuse `export/file_saver` (web download, share sheet on Android and iOS).
- The report uses a summary card and tabs for days, services, and customers. Lists only, no charts in the first version.
- Salon terms are a small mapping in the app, not a full translation system.

## Risks / Trade-offs

- [The MODIFIED deltas depend on the in-flight changes] → Archive `add-pos-ordering-system`, `add-table-management`, and `harden-printing-for-events` before this change. `openspec validate` confirms it, and its info messages for the missing target specs are expected until then.
- [Renaming `printer.item_type` touches the printing code from the in-flight change] → Done in one migration with the code change, covered by the existing printing tests.
- [The invoice number lock serializes issuing] → Holding the lock takes milliseconds. A salon issues a few invoices per hour.
- [Invoices are wrongly issued, and staff cannot correct them] → Admins cancel and issue again. Numbers stay gapless, and the cancelled invoice stays visible.
- [Forgotten invoices: confirmed orders that never get an invoice do not count as revenue] → The order history shows `not invoiced`, and "Confirm and invoice" is the default button in salon mode.
- [Double values in Excel can show tiny rounding remainders in user formulas] → Amounts are written as Rappen / 100 with two decimals. All sums in the file are computed on the server from integers.
- [10-year retention of invoices on a small on-site machine] → The backup routine from `add-pos-ordering-system` (Migration Plan) also covers the new tables. The setup guide recommends an off-site backup copy for salons.
- [Personal data on a device at the counter] → Customer endpoints need a login, sessions expire after 12 hours, and anonymization covers deletion requests. Transport security follows D11 of `add-pos-ordering-system`.

## Migration Plan

1. Migrations V10 to V14 (settings, customers, order and menu changes, invoices and payments, printing changes). `installation_settings` gets one row with `mode = 'event'`. Existing menu items get `vat_rate_permille = 81` and existing order lines get `vat_rate_snapshot = 81`. Neither is used in event mode.
2. Deploy the backend and the app together. Event installations see no change.
3. Salon setup: install, log in as admin, set mode `salon`, enter the business details and VAT, add a receipt printer, create services, and add staff.
4. Rollback: before the first salon order, the previous version can be deployed again with a down-migration script that drops the new tables and columns (tested once). After salon data exists, no rollback: fix forward.

## Open Questions

- The exact invoice footer text (payment terms, a thank-you line) can be decided with the first salon and added as a settings field without changing the design.
- Whether the A4 PDF should show the salon's logo. It can be added later as an optional settings image.
