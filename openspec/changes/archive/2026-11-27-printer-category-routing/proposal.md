# Proposal

## Why

Printers are tied to the fixed item types `food` and `drink`, and in salon mode only to `receipt`. The admin app was never updated for `receipt`, so in salon mode no printer can be added at all. The fixed types also don't fit real venues: a pizza station, a dessert pass, or a second bar can't be set up. Printing should follow the categories the admin creates. This is the first step toward one app for restaurants, events, and salons, where modes become templates of features (variant A).

## What Changes

- **BREAKING** A printer no longer has one `type`/`purpose` (`food`, `drink`, `receipt`). Instead it has:
  - a **Tickets (Bons)** switch and a list of **categories** whose lines it prints,
  - a **Receipts (Rechnungen)** switch for invoice receipts.
- A category can be assigned to **several** printers (n:m). Sending an order creates one ticket per active printer, holding the lines of that printer's categories. A line whose category goes to two active printers is printed on both.
- Printing is opt-in per category. A category that is not assigned to any printer is deliberately **not printed**: no ticket and no error. This replaces today's `failed` ticket "no printer configured" for categories without a printer.
- A category that is assigned only to printers that are all **inactive** creates a `failed` ticket "no active printer", so no order is missed (e.g. the bar printer was switched off by mistake). If at least one assigned printer is active, only the active ones print.
- Cancellation (STORNO) and reprint tickets go to the printers of the line's category, as stored when the order was sent.
- Whether a line is printed depends only on its category's printer assignment, not on the item type. A salon that assigns no printer to its service categories prints no tickets, as today. The `service` item type is no longer a special case for printing.
- Both switches can be set on every printer in every mode. The server no longer rejects printers based on the venue mode. Receipts are still only produced where invoices exist (salon mode until the feature switches of step 2).
- An invoice receipt is printed on **every** active printer with receipts on, as one print job per printer with its own status. A copy ("KOPIE") is printed on all of them as well. With no printer set to receipts, no receipt is printed; the invoice stays valid and its PDF is available. If printers have receipts on but all of them are inactive, the receipt job is `failed` with "no active printer", the same as for tickets.
- The rule "exactly one active printer per type" is dropped, and so is the error-style warning about missing printers. The printer page instead lists, as neutral information, the categories that are not printed, so an oversight stays visible without counting as an error.
- The admin app's printer dialog replaces the Food/Drinks selector with the two switches and a category multi-select. The printer list shows each printer's categories and switches. This fixes adding printers in salon mode.
- Migration: each existing `food`/`drink` printer gets tickets on and the categories that contain items of its type. Each `receipt` printer gets receipts on and tickets off.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `ticket-printing`: printer configuration (switches and categories instead of one type, no mode restriction, no "one printer per type" rule), routing of order, cancellation, and reprint tickets by category (unassigned categories are not printed, categories with only inactive printers fail), the "no production tickets for services" rule replaced by category assignment, and receipt jobs per printer with receipts on.
- `invoicing`: "Receipt on the receipt printer" becomes "on every active printer with receipts on", including copies and the case with no receipt printer.

## Impact

- **Depends on** `add-salon-mode` and `harden-printing-for-events`, which change the same `ticket-printing` requirements and are not archived yet. Archive them first, or base the deltas on their versions.
- **DB:** new migration. A `printer_category` join table, `printer.prints_tickets` and `printer.prints_receipts`, `printer.purpose` and `printer_active_purpose_uq` dropped. `print_job.item_type` is replaced by `ticket`/`receipt`. Order lines need the printers or the category stored when sent, so that STORNO and reprint don't follow later changes to the assignment.
- **Backend:** `PrintPurpose`, `PrinterService.check`, `PrinterRepository.findOtherActiveFor`, `PrintJobs.byType`/`create`, receipt routing in `InvoiceReceipts`, `PrinterAdminController` DTOs and OpenAPI, the station view (`StationRepository`, which shows `p.purpose`), and the tests under `printing/`, `order/`, and `station/`.
- **App:** `printers_api.dart` (`PrinterData` without `ItemType`, which also fixes the crash on parsing `receipt`), `printers_screen.dart` (dialog, list, warnings), a categories endpoint for the multi-select, `test/printing_test.dart`, and the fake backend.
- **Out of scope:** replacing the venue mode with feature switches (step 2) and removing the item types (step 3).
