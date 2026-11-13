# Tasks

## 1. Dependencies and data model (design D1, D3 to D8)

- [ ] 1.1 Add OpenPDF and Apache POI (`poi-ooxml`) to `backend/pom.xml`, and add Liberation Sans to the backend resources with its license file. Verify that `./mvnw -q verify -DskipTests` succeeds and the font loads from the classpath in a small test
- [ ] 1.2 Add `V10__installation_settings.sql`: the single-row `installation_settings` table (check `id = 1`) with `mode` (`event` or `salon`, default `event`), the business details, `vat_enabled`, and `vat_uid`, and insert the row. Verify with `MigrationTest` that a fresh and an upgraded database both have exactly one row in mode `event`
- [ ] 1.3 Add `V11__customers.sql`: `customer` with its own number sequence, `phone_digits`, `anonymized_at`, and an index on `lower(last_name)`. Verify with a schema test that numbers are unique and a deleted customer's number is not given again
- [ ] 1.4 Add `V12__salon_orders.sql`: `service` in the type checks of `menu_item` and `order_line.type_snapshot`, `menu_item.duration_min`, `menu_item.vat_rate_permille` (default 81), `order_line.vat_rate_snapshot` (existing rows 81), nullable `orders.table_label`, `orders.customer_id`, and a check that requires exactly one of table label and customer. Verify with `UpgradeMigrationTest` that existing orders and items stay valid, and with `OrderSchemaTest` that an order with both or neither is rejected
- [ ] 1.5 Add `V13__invoices.sql`: `invoice`, `invoice_line`, `invoice_vat`, `invoice_counter`, `invoice_payment`, the partial unique index for one active invoice per order, and one active payment per invoice. Verify with a schema test that a second active invoice for an order and a second active payment for an invoice are rejected
- [ ] 1.6 Add `V14__receipt_printing.sql`: rename `printer.item_type` to `purpose` (also `receipt`), add nullable `print_job.invoice_id`, make `order_id` nullable with a check that requires one of them, allow `receipt` as job type and `copy` as kind. Verify that all existing printing tests (`PrintWorkerTest`, `ReprintTest`, `PrinterAdminTest`, `MissingPrinterTest`) still pass after the code is adjusted to `purpose`

## 2. Backend: settings and mode (spec: venue-mode, staff-access; design D1, D2)

- [ ] 2.1 Implement `GET /settings` (any logged-in user) and `PUT /settings` (admin) for mode, business details, IBAN (CH/LI, check digits), and VAT with UID check (format and mod-11 check digit). Verify with `SettingsControllerTest`: invalid IBAN and UID rejected, VAT on without UID rejected, waiter gets 403 on `PUT`
- [ ] 2.2 Implement the mode change with a row lock on the settings row and the check that `orders` is empty. Verify with tests: change succeeds without orders, is rejected with a message when an order exists, and a concurrent order send and mode change never leave both in place
- [ ] 2.3 Add `@RequiresMode` and the interceptor (409 `wrong_mode`), and annotate table and station endpoints with `EVENT`. Reject station logins in salon mode. Verify with `PermissionTest` cases: table endpoints and station login rejected in salon mode, event endpoints unchanged in event mode
- [ ] 2.4 Document the settings endpoints in OpenAPI and verify that `OpenApiTest` lists them

## 3. Backend: services and VAT rates (spec: menu-catalog; design D6)

- [ ] 3.1 Extend item admin with type `service`, duration 5 to 600 minutes, and VAT rate (0, 26, 38, 81 permille; default 81), and enforce the item types per mode. Verify with `ItemAdminTest`: service created in salon mode, `food` rejected in salon mode, `service` rejected in event mode, invalid duration and rate 7.7 % rejected
- [ ] 3.2 Return duration and VAT rate in the waiter menu, and only services in salon mode. Verify with `WaiterMenuTest`

## 4. Backend: customers (spec: customer-management; design D4)

- [ ] 4.1 Implement customer create, edit, get, and search (`/customers`, salon mode only) with validation, case-insensitive name/email/number search, and phone search on digits only. Verify with `CustomerControllerTest`: name-only create, invalid email and missing name rejected, search for "mei" and "079 555" finds the expected customers, inactive customers only for admins
- [ ] 4.2 Implement `GET /customers/duplicates` (same full name, phone, or email). Verify with a test that a matching name, phone, and email each return the existing customer, and that creating anyway still works
- [ ] 4.3 Implement deactivate/reactivate (admin) and the customer history (invoices newest first with the sum of not cancelled invoices). Verify with tests, including that a deactivated customer cannot be used for a new order
- [ ] 4.4 Implement delete: full delete without orders, anonymization with orders (admin only), and `GET /customers/{id}/export` as JSON. Verify with tests: deleted customer not found, anonymized customer shows "Deleted customer" while its issued invoices keep the original details, staff get 403, the export contains record, invoices, lines, and payments
- [ ] 4.5 Document the customer endpoints in OpenAPI and verify that `OpenApiTest` lists them

## 5. Backend: salon orders (spec: order-taking, ticket-printing; design D3, D8)

- [ ] 5.1 In salon mode, the send endpoint requires an active customer and only active services, rejects a table label, and snapshots the VAT rate. Verify with `SendOrderTest` salon cases: confirm for a customer, no customer rejected, inactive customer rejected and named, double-tap saves one order, event mode cases unchanged
- [ ] 5.2 Skip `service` lines in production ticket routing, cancellation tickets, and voids. Verify with tests that confirming, cancelling a line, and voiding a salon order create no print jobs
- [ ] 5.3 Return customer name, customer number, and invoice status (`not invoiced`, number with `open`/`paid`, cancelled) in the order history and detail in salon mode. Verify with `OrderHistoryTest` salon cases

## 6. Backend: invoicing (spec: invoicing; design D5, D6)

- [ ] 6.1 Implement `POST /orders/{id}/invoice` with idempotency key: checks (salon mode, business details complete, order not voided, has a customer and at least one line not cancelled, no active invoice), row lock on the order, the gapless number from `invoice_counter`, and the snapshot of business, customer, lines, and VAT. Verify with `IssueInvoiceTest`: first invoice of a year is `YYYY-0001`, second invoice for the same order rejected, voided order rejected, missing address rejected with the message, double request creates one invoice
- [ ] 6.2 Calculate VAT per rate on the gross amount with half-up rounding when VAT is on. Verify with `VatCalculationTest`: 108.10 at 8.1 % gives VAT 8.10 and net 100.00, mixed rates, VAT off gives no VAT rows
- [ ] 6.3 Block line cancel and void while an active invoice exists ("cancel the invoice first"). Verify with `CancelAndVoidTest` salon cases
- [ ] 6.4 Implement invoice cancel (admin, reason, rejected while paid) and the invoice list (period, status filter, search by number and customer name) and detail. Verify with tests: cancelled invoice keeps its number, order can be corrected and invoiced again with the next number, staff get 403 on cancel, list filters by `open`
- [ ] 6.5 Add a concurrency test: 20 invoices issued in parallel get consecutive numbers without gaps or duplicates, and a failed issue in the middle leaves no gap. Verify that the test passes repeatedly (10 runs)
- [ ] 6.6 Verify with a test that changing the settings, customer, or service price after issuing does not change the invoice detail

## 7. Backend: payments (spec: invoicing; design D7)

- [ ] 7.1 Implement `POST /invoices/{id}/payment` (method, date not before invoice date and not in the future, recorded by) and `DELETE /invoices/{id}/payment` (admin, reason, keeps the removed row). Verify with `InvoicePaymentTest`: TWINT payment today, future date rejected, cancelled invoice cannot be paid, undo by admin keeps history, staff get 403 on undo
- [ ] 7.2 Document the invoice and payment endpoints in OpenAPI and verify that `OpenApiTest` lists them

## 8. Backend: receipts (spec: ticket-printing, invoicing; design D8)

- [ ] 8.1 Add `ReceiptRenderer` for 48 columns with business block, number, date, customer, lines, total, VAT breakdown, and UID, and the markers "KOPIE" and "STORNIERT". Verify with `ReceiptRenderingTest` snapshots for a receipt with and without VAT, a copy, and a cancelled receipt
- [ ] 8.2 Insert the receipt job in the issue transaction on the active `receipt` printer (or `failed` "no printer configured"), and add `POST /invoices/{id}/receipt` for a copy that also resolves an unclear receipt. Verify with tests against `FakeEposPrinter`: issuing prints once, paper end waits and prints after recovery, unclear receipt is not sent again, a copy prints "KOPIE", no printer gives `failed`
- [ ] 8.3 Enforce one active `receipt` printer in salon mode and allow configuring it in printer admin. Verify with `PrinterAdminTest`: second active receipt printer rejected
- [ ] 8.4 Return the latest receipt job status and error in the invoice detail. Verify with a test
- [ ] 8.5 Add receipts and the receipt printer to `docs/printing.md`. Verify that the documented states match the code

## 9. Backend: A4 PDF (spec: invoicing; design D9)

- [ ] 9.1 Implement `GET /invoices/{id}/pdf` with OpenPDF (A4, embedded font, address for a C5 window envelope, line and VAT tables, bank details, "STORNIERT" marker), named `Rechnung-<number>.pdf`. Verify with `InvoicePdfTest`: the extracted text contains number, customer, lines, total, and umlauts, the page is A4, a cancelled invoice shows "STORNIERT", and two downloads have the same text content

## 10. Backend: sales report and Excel (spec: sales-reporting; design D10)

- [ ] 10.1 Implement `ReportService` and `GET /reports/sales?from&to` (admin, salon mode, max 366 days): summary, days (including days without revenue), services, customers, payment methods, and VAT per rate. Verify with `SalesReportTest` using a fixed data set: cancelled invoice excluded, invoice date counts and not the order date, paid/open amounts, day 0.00 listed, service and customer sorting, anonymized customer shown as "Deleted customer", 400 days rejected, staff get 403
- [ ] 10.2 Implement `GET /reports/sales.xlsx` with Apache POI from the same report object: the six sheets with header rows, numeric amounts with `#,##0.00`, date cells, cancelled invoices on "Rechnungen" with status "storniert". Verify with `SalesReportExcelTest` that reads the file back: sheet names, cell types, the sum of "Tage" equals the total on "Übersicht" and the JSON report
- [ ] 10.3 Document the report endpoints in OpenAPI and verify that `OpenApiTest` lists them

## 11. App: settings and mode (spec: venue-mode; design D11)

- [ ] 11.1 Add `settings_api.dart` and a settings controller that loads after login and on app resume, and build the navigation in `home` from the mode (salon: Order, Customers, Invoices, Report for admins, Admin). Verify with widget tests that salon mode hides tables and station and event mode hides customers, invoices, and report
- [ ] 11.2 Add the settings screen (admin): mode with a clear message when orders exist, business details, VAT on/off with UID, bank details. Verify with widget tests for saving and for the validation errors shown from the server
- [ ] 11.3 Add the salon terms mapping ("staff", "services", "confirm") and use it in the order screen, menu admin, and staff admin. Verify with widget tests in salon mode

## 12. App: services and customers (spec: menu-catalog, customer-management; design D11)

- [ ] 12.1 Extend `menu_admin_screen.dart` with type `service`, duration, and VAT rate (shown only with VAT on), and show duration and the service placeholder on the tiles. Verify with `menu_admin_test.dart` and `menu_view_test.dart` cases
- [ ] 12.2 Add `app/lib/customers/` with list and search, editor, duplicate warning dialog, customer history, deactivate, delete (admin, with confirmation), and data export download. Verify with `customers_test.dart`: search, create with warning, history list and sum, delete only offered to admins

## 13. App: salon order flow (spec: order-taking, invoicing; design D11)

- [ ] 13.1 Add the customer picker with "New customer" to the order screen in salon mode, and "Confirm and invoice" as the default button with "Confirm only" as a second action. Use separate idempotency keys for confirm and issue. Verify with widget tests: no customer blocks confirm, the two-step flow keeps the order confirmed when issuing fails and shows the reason
- [ ] 13.2 Show customer and invoice status in the order history and allow "Issue invoice" for not invoiced orders. Verify with `orders_test.dart` salon cases

## 14. App: invoices (spec: invoicing, ticket-printing; design D11)

- [ ] 14.1 Add `app/lib/invoices/` with the invoice list (period, status filter, search) and invoice view (content, receipt status with error, "Print copy", "Download PDF", "Mark as paid" with method and date, "Cancel" and "Undo payment" for admins). Verify with `invoices_test.dart`: actions per role, payment dialog validation, receipt error shown
- [ ] 14.2 Download the PDF through `export/file_saver` on web, Android, and iOS. Verify on web in a widget test and manually on one Android and one iOS device

## 15. App: sales report (spec: sales-reporting; design D11)

- [ ] 15.1 Add `app/lib/report/` with month picker and date range, summary card, and tabs for days, services, and customers (admin only), plus the Excel download. Verify with `report_test.dart` against a fake API: summary values, empty days shown, download triggered, not visible for staff

## 16. Salon operation docs

- [ ] 16.1 Write `docs/salon-setup.md`: installation, switching to salon mode, business details and VAT, receipt printer setup, services, staff, daily use (confirm and invoice, payment, copy, cancel), month-end report and Excel, data protection requests, and backups with an off-site copy for the 10-year retention. Verify by setting up a fresh installation as a salon using only the guide

## 17. Integration checks

- [ ] 17.1 Run backend tests, `flutter analyze`, and `flutter test`, and verify that everything passes
- [ ] 17.2 End-to-end test with a real TM-m30III: set up a salon, create customers and services with VAT on, confirm and invoice several orders from web and a phone, pull the receipt paper during issuing, cancel and reissue one invoice, mark payments with different methods, and export the month. Verify that receipt, PDF, app report, and Excel show the same numbers, invoice numbers have no gaps, and record the results in `docs/acceptance.md`
- [ ] 17.3 Regression check in event mode: run the event acceptance steps from `harden-printing-for-events` 10.4 on the upgraded version. Verify that tickets, stations, and exports behave as before

## Workflow follow-up

- Archive `add-pos-ordering-system`, `add-table-management`, and `harden-printing-for-events` first, then this change, because its deltas modify requirements those changes add.
- Open questions from design.md (invoice footer text, logo on the PDF) can be added later as settings fields without a spec change.
