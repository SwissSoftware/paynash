# Tasks

## 1. Database migration

- [x] 1.1 Write `V15__printer_categories.sql` per design "Migration Plan": create `printer_category` and `order_line_printer`, add `printer.prints_tickets` / `prints_receipts`, backfill both, drop `printer.purpose`, `printer_active_purpose_uq`, `print_job_receipt_check`, `print_job.item_type`. Verify with an `UpgradeMigrationTest` case: a V14 DB with a food, a drink and a receipt printer, a mixed category and sent lines migrates to the expected switches, assignments and `order_line_printer` rows
- [x] 1.2 Extend `OrderSchemaTest` / `InvoiceSchemaTest` for the new tables and dropped columns (cascade on category and printer delete, PK uniqueness) and verify they pass

## 2. Printer configuration (backend)

- [x] 2.1 Replace `PrintPurpose` in `Printer`, `PrinterRepository` (insert/update/find with switches and category ids, drop `findActiveFor` / `findOtherActiveFor`) and delete `PrintPurpose`. Verify that the backend compiles once groups 3–4 are done; until then keep a temporary adapter
- [x] 2.2 Rewrite `PrinterService.check`: unique name and existing `categoryIds` only, no mode or one-per-purpose rule. Update `PrinterAdminController` DTOs (`printsTickets`, `printsReceipts`, `categoryIds`) and OpenAPI text. Verify with `PrinterAdminTest`: receipt printer in salon and event mode, ticket printer in salon mode, two active receipt printers, category on two printers, unknown category id rejected
- [x] 2.3 Add the "not printed" categories to the printer admin API (design D7) and verify with a `PrinterAdminTest` case for an unassigned category and a category only on an inactive printer
- [x] 2.4 Update `TicketRenderer.testTicket` to list categories and receipts (design D8) and verify with `TicketRenderingTest`

## 3. Ticket routing by category (backend)

- [x] 3.1 Resolve each line's category printers from its `menuItemId` (`menu_item.category_id`, see design D3) and verify the existing order tests still pass
- [x] 3.2 Replace `PrintJobs.byType` with `byPrinter` and change `create` to target a printer (design D3); write `order_line_printer` rows when sending. Verify with `SendOrderTest` / `PrintWorkerTest` cases for spec "Route lines by category": mixed order, drinks only, category on two printers, category not printed, all assigned printers inactive leads to failed "no active printer" per printer, one of two inactive, printer with tickets off
- [x] 3.3 Route STORNO and void through `order_line_printer` (design D4) and verify with `CancelAndVoidTest`: two printers, assignment changed after sending, line that was not printed, void with at most one ticket per printer
- [x] 3.4 Route reprints to the original job's printer, failing with "no active printer" when it is inactive now. Verify with `ReprintTest` and `MissingPrinterTest` (rewritten for "no active printer")
- [x] 3.5 Replace the services special case: delete the `ItemType.SERVICE` checks in `PrintJobs`. Verify with `SalonOrderTest` that a salon without assigned categories creates no tickets, and with a service category assigned it prints
- [x] 3.6 Update `TicketNumberTest`, `FestivalLoadTest`, `PrinterFaultTest`, `OrderPrintStatusTest`, `OrderHistoryTest` and `TicketView` (drop `type`) and verify that `./mvnw test` passes for the `printing` and `order` packages

## 4. Receipts on multiple printers (backend)

- [x] 4.1 Make `queueReceipt` create one job per printer with receipts on (design D5), with no job when none has receipts on. Verify with `ReceiptPrintingTest`: two receipt printers, all inactive leads to failed per printer, none leads to no job, copy on all printers, copy rejected while any job is in progress
- [x] 4.2 Add `receipts` (latest job per printer) to `InvoiceController.InvoiceView` next to `receipt`, and return a list from the copy endpoint, and `InvoiceReceipts.latest` accordingly. Verify with `IssueInvoiceTest` / `CancelAndListInvoiceTest`

## 5. Stations (backend)

- [x] 5.1 Replace `type` in `StationRepository.AssignedPrinter` and `StationController.StationPrinterView` with the switches, and verify with `StationResolveTest` / `StationCancelTest` and the full `./mvnw test`

## 6. App: printer admin

- [x] 6.1 Update `PrinterData` / `PrintersApi` to the new shape (no `ItemType`) and verify with a `printing_test.dart` case that parses a receipt-only printer without error
- [x] 6.2 Rebuild the printer dialog: "Tickets" and "Receipts" switches and a category multi-select loaded from `MenuAdminApi.categories()`, with field errors. Verify with widget tests that add a receipt printer in salon mode and a ticket printer with two categories
- [x] 6.3 Replace the red "No active printer for …" banners with the neutral "Not printed: …" info and show switches and categories per printer in the list. Verify with widget tests
- [x] 6.4 Update `test/support/fake_backend.dart` to the new printer, ticket and invoice API shapes and verify that `flutter test` passes

## 7. App: tickets, stations, invoices

- [x] 7.1 Remove the ticket `type` fallback in `order_detail_screen.dart` (use "Ticket" when `printer` is null) and adapt `station_models.dart`. Verify with `orders_test.dart` / `station_test.dart`
- [x] 7.2 Show one receipt status per printer on the invoice screen and warn when no receipt was printed (empty list or all failed). Verify with `invoices_test.dart`

## 8. Integration check

- [ ] 8.1 Run `./mvnw verify` and `flutter analyze && flutter test`, then manually check with the docker setup: in salon mode add a receipt printer and issue an invoice; in event mode assign a category to two printers and send, cancel and reprint an order. Verify that the tickets appear on the expected printers or stations → moved to hardware-acceptance

## Workflow follow-up

- Archive `add-salon-mode` and `harden-printing-for-events` before archiving this change (their deltas create the requirements this change modifies).
- Archive this change after review, and check that the merged `ticket-printing` and `invoicing` specs read correctly.
