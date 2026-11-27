# Tasks

## 1. Data model and feature dependency

- [x] 1.1 Add Flyway V17 (design D1): `invoice.customer_id` and `invoice.customer` nullable with the check that both are null or both set, and drop `installation_settings_invoices_need_customers`. Extend `UpgradeMigrationTest` with a database at V16 holding an invoice with customer, migrate to V17, and verify the invoice is unchanged and an invoice row without customer and an installation with `invoices` but without `customers` are accepted.
- [x] 1.2 Remove the dependency `INVOICES` → `CUSTOMERS` in `Feature` and keep `SALES_REPORT` → `INVOICES`. Update `SettingsControllerTest` for the scenarios of "Sales report needs invoices" (report without invoices rejected, invoices still needed by the report, invoices without customers accepted) and remove the two tests of the old dependency.

## 2. Issuing invoices without customer (backend)

- [x] 2.1 Make the customer optional in `Invoice` (`Long customerId`, nullable `CustomerCopy`), `InvoiceRepository` (insert, read, list), and `InvoiceController` views (customer number and name nullable). Verify the existing invoice tests still pass.
- [x] 2.2 In `InvoiceService.issue`, allow orders without customer and enforce the CHF 400.00 limit before numbering (design D2, 409 `customer_required`). Verify with tests for "Order without customer", "Exactly at the limit" (400.00 issued), "Above the limit" (400.05 rejected and the next invoice still gets the next number), and "Large invoice with customer" (1250.00 issued). Replace the test that expected the old rejection.
- [x] 2.3 `ReceiptRenderer` and `InvoicePdf` without recipient (design D3). Verify with rendering tests that an invoice without customer contains business details, number, lines, and total, and no "Kunde Nr.", and that receipts with customer are unchanged.
- [x] 2.4 `OrderService`: show the invoice of every order and apply the "Invoices are final" check to every order (design D5). Verify that cancelling a line or voiding a table order without customer is rejected while its invoice is not cancelled, and that the history shows its invoice status.

## 3. Report (backend)

- [x] 3.1 `ReportService` with outer joins and the group "Ohne Kunde"; `SalesReport` customer number nullable; `SalesReportExcel` writes empty cells (design D4). Verify with the scenario "Counter sales without customer" (order and totals), and that the Excel sheets "Kunden", "Rechnungen", and "Positionen" contain the invoices without customer and the same totals as the report.

## 4. App

- [x] 4.1 Invoice models with optional customer number, name, and address; invoice screen and invoice list show "Ohne Kunde" and no number. Update `invoices_test.dart` with an invoice without customer.
- [x] 4.2 Order screen and order detail screen (design D6): send and invoice without customer, "Issue invoice" for every not invoiced order while invoices are on. Extend `business_features_test.dart`: counter sale with "Sell and invoice", the CHF 400 message, and issuing from the history; update the fake backend to issue invoices without customer with the limit.
- [x] 4.3 Report screen shows "Ohne Kunde"; settings screen no longer shows "Needs customers" under Invoices. Update `report_test.dart` and `settings_test.dart`.

## 5. Docs and integration

- [x] 5.1 Update `docs/counter-setup.md`, `docs/event-setup.md`, `docs/salon-setup.md`, and `docs/printing.md`: receipts without customer, the CHF 400 limit, and switching on invoices and the report without customers. Copy the regenerated `backend/target/openapi.json` to `docs/api/openapi.json`.
- [x] 5.2 Run `./mvnw verify` in `backend/` (JDK 21), `flutter test` and `flutter analyze` in `app/`, and verify all are green. Then, in a local installation, switch on invoices and the sales report in the Counter sales template, sell and invoice once below and once above CHF 400, and check receipt, PDF, invoice list, and report.
