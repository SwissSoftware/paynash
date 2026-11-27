# Design

## Context

See proposal.md - Why. Current state of printing (after `add-salon-mode` D8 and `harden-printing-for-events`):

- `printer.purpose` is `food | drink | receipt`. The partial unique index `printer_active_purpose_uq` allows one active printer per purpose. `PrinterService.check` rejects purposes that don't fit the venue mode.
- `PrintJobs.create(…, PrintPurpose, …)` resolves *the one* active printer of a purpose via `PrinterRepository.findActiveFor`. If there is none, it inserts a `failed` job with `printer_id = NULL` ("no printer configured") and no ticket number.
- `PrintJobs.byType` splits sent lines by `ItemType` (drinks first, then food) and skips `service`. STORNO uses `PrintPurpose.of(line.type())`. A reprint re-resolves the current printer of the original job's purpose.
- `print_job.item_type` holds the purpose. The check `print_job_receipt_check` ties `receipt` to `invoice_id`.
- `order_line` stores `type_snapshot` and `category_snapshot` (the name only, not the id). Nothing records which printer a line was printed on.
- Ticket numbers are per printer (`printer.last_ticket_no`) and are assigned when a job is created for a printer.
- The invoice view returns one `receipt` (the latest receipt job). `InvoiceReceipts.copy` is rejected while any receipt job is in progress.
- App: `PrinterData.type` is an `ItemType` and the dialog offers only food and drink. `order_detail_screen.dart` falls back to the ticket `type` when a ticket has no printer.

## Goals / Non-Goals

**Goals:**
- Route production tickets per printer from a category-to-printer assignment (n:m), and receipts to every printer with receipts on.
- Every print job has a concrete printer, so a failed job can always be reprinted to a known place, and stations see it.
- Keep the outbox, worker, waiting, crash recovery and ticket number logic unchanged. Only the routing in front of them changes.

**Non-Goals:**
- Feature switches instead of venue mode (step 2). `@RequiresMode` stays, so invoices and receipts still only exist in salon mode.
- Removing or changing `ItemType` on menu items (step 3). Item types stay for menu admin and badges. They just no longer drive printing.
- Choosing a receipt printer per device or per staff member.

## Decisions

### D1: Data model
- New table `printer_category (printer_id → printer ON DELETE CASCADE, category_id → category ON DELETE CASCADE, PK (printer_id, category_id))`. Deleting a category silently drops its assignments.
- `printer` gets `prints_tickets BOOLEAN NOT NULL` and `prints_receipts BOOLEAN NOT NULL`. `purpose` and `printer_active_purpose_uq` are dropped.
- Assignments are kept when `prints_tickets` is switched off, so switching it on again restores them. Routing ignores them while the switch is off.
- *Alternative*: `category.printer_id`. Rejected because a category can go to several printers.

### D2: Recording where a line was printed
New table `order_line_printer (order_line_id, printer_id, PK both)`, written in the send transaction for each printer a line was routed to, including the inactive printers of a "no active printer" failure. STORNO and void group cancelled lines by these rows. This implements "printers as stored when sent" (spec: Cancellation tickets).
- *Alternative*: store `category_id` on the line and re-resolve. Rejected because STORNO would follow later assignment changes, which the spec forbids.
- *Alternative*: a job-to-line link table. Equivalent but needs a join through `print_job`. Per-line rows are simpler to query.

### D3: Routing algorithm (`PrintJobs`)
`byType` is replaced by `byPrinter(lines)`:
1. Load the assignments for the lines' categories: `menu_item` (by the line's `menuItemId`) joined with `printer_category` and `printer` where `prints_tickets`. `OrderLine` stays unchanged.
2. For each line: targets = active assigned printers. If empty and there are assigned printers that are all inactive, targets = those inactive printers, marked as a failure. If there are no assigned printers, skip the line.
3. Group by target printer, keeping the line order. Order the printers by name so the result is predictable.
4. Per active printer, insert a `pending` job with the next ticket number, as today. Per inactive target, insert a `failed` job with `printer_id` set, status reason "no active printer", and no ticket number. A reprint then numbers it the same way as today's unnumbered failures.

`create` takes a `Printer` (or printer id plus an active flag) instead of a `PrintPurpose`. `findActiveFor` and `findOtherActiveFor` are removed.

### D4: STORNO, void, reprint
- STORNO and void: targets come from `order_line_printer` for the cancelled lines, with the same active/inactive rule as D3. Lines without rows are not printed (spec: "Cancel a line that was not printed").
- Reprint: goes to the original job's `printer_id`, which is now always set. If that printer is inactive now, the reprint is `failed` "no active printer". Today's behavior was "the current printer of the type", which no longer exists with n:m.

### D5: Receipts
`queueReceipt` creates one job per printer with `prints_receipts`, using the D3 rule: active ones `pending`, all inactive leads to `failed` per printer, none leads to no job. It returns the list of jobs. `InvoiceReceipts.copy` keeps the in-progress check across all receipt jobs. The invoice API keeps `receipt` (latest job overall, for compatibility) and adds `receipts` (latest job per printer). `POST /invoices/{id}/receipt` returns the list of copy jobs (empty when no printer has receipts on). The app's "no receipt printed" warning fires when the list is empty or every entry is failed.

### D6: `print_job.item_type`
The column is dropped. Whether a job is a ticket or a receipt already follows from `order_id` / `invoice_id`. `print_job_receipt_check` is replaced by the existing `print_job_source_check`. `PrintPurpose` is deleted. `TicketView.type` is removed from the API. The app falls back to "Ticket" when `printer` is null, which only happens for jobs created before this migration.

### D7: Admin API and app
- `PrinterView` / `PrinterInput`: `printsTickets`, `printsReceipts`, `categoryIds: [..]` replace `type`. Validation: unique name. Category ids must exist (`categoryIds` error). There is no mode check and no one-per-purpose check.
- `GET /admin/printers` also returns `unprintedCategories: [{id, name}]`, or it is a separate `GET /admin/printers/unprinted-categories`. The list is computed server-side: categories with no active printer that has tickets on.
- App: the dialog has two `SwitchListTile`s, and under "Tickets" a `FilterChip` multi-select of categories loaded via `MenuAdminApi.categories()`. The list tile shows the switches and category names. The red "No active printer for …" banners are replaced by one neutral info tile "Not printed: Entrance, …".
- `PrinterData.fromJson` no longer touches `ItemType`, which removes the crash on `receipt`.
- Station API: `StationPrinterView.type` is replaced by `printsTickets` / `printsReceipts`. Stations keep listing jobs by `printer_id`.

### D8: Ticket text
`TicketRenderer.testTicket` prints "Prints tickets: Beer, Soft drinks" and/or "Prints receipts" instead of the purpose.

## Risks / Trade-offs

- [Pending changes not archived] The deltas modify requirements that only exist after archiving `add-salon-mode` and `harden-printing-for-events` (`openspec validate` reports this as INFO). → Archive both before archiving this change.
- [Duplicate tickets by misconfiguration] A category assigned to two printers by mistake prints twice. → The printer list shows the categories per printer, and the test ticket lists them.
- [Silent non-printing] An unassigned category is never printed and never raises an error. → Neutral "not printed" list on the printer page (spec: Categories that are not printed).
- [Backfill gaps] Lines sent before the migration whose ticket failed without a printer get no `order_line_printer` rows, so a later STORNO prints nothing. → Acceptable: those lines were never printed either. Lines with a printed job are backfilled.
- [Mixed categories in migration] A category holding both food and drink items gets both printers, so after the migration all its lines print on both, where before each type printed on its own printer. → The migration logs these categories, and the release notes ask admins to split them or fix the assignment.
- [Multiple receipt jobs] The invoice UI has to handle several statuses. → A per-printer list; the copy action stays a single button for all printers.

## Migration Plan

`V15__printer_categories.sql`, in one transaction:
1. Create `printer_category` and `order_line_printer`. Add `prints_tickets` and `prints_receipts` to `printer` with default false.
2. Backfill the printers: `purpose IN ('food','drink')` → `prints_tickets = true`, plus `printer_category` rows for every category that contains a menu item of that type (active or not). `purpose = 'receipt'` → `prints_receipts = true`.
3. Backfill `order_line_printer` for sent lines: the `printer_id` of the order's non-reprint jobs where `item_type = type_snapshot` and `printer_id IS NOT NULL`.
4. Drop `printer_active_purpose_uq`, `printer.purpose`, `print_job_receipt_check`, and `print_job.item_type`.

Rollback: the migration is forward-only like V1–V14. Restore from the pre-deploy DB backup. Since `purpose` is derivable from the switches only when the setup is unambiguous, a down-migration is not provided.

Deploy: backend and app together (the printer API changes shape). Afterwards an admin checks the printer page once. The migrated assignments reproduce today's behavior: every food category goes to the food printer, every drink category to the drink printer, and service categories go nowhere.

## Open Questions

- Should the "not printed" information also appear in the category list of the menu admin? This can be added later without changing the specs above.
