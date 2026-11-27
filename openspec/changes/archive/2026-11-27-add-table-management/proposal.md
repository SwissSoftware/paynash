# Proposal

## Why

Waiters currently type the table label by hand when taking an order. This is slow on a phone and error-prone: typos like "21" instead of "12" or "T5" instead of "5" create orders on tables that do not exist, and the kitchen, bar, and the order history then show the wrong table. If the admin keeps a list of the restaurant's real tables, waiters can tap a table instead of typing it.

## What Changes

- Admins can manage tables in the admin UI. Each table has a **number** (the short label used on orders, tickets, and in the export, 1 to 10 alphanumeric characters, unique) and an optional **name** or description (e.g. "Terrace window", "Bar counter").
- Admins can create, edit, deactivate, and delete tables. Deactivating hides a table from waiters temporarily (e.g. terrace tables in winter). Deleting removes it permanently. Sent orders store the table label as text, so they keep their label when a table is renamed, renumbered, or deleted.
- When taking an order, the waiter sees a list of active tables showing number and name, and can pick one. Picking a table only sets the table of the order being built; the waiter then adds items and sends the order as before.
- Manual entry stays as a fallback. A waiter can still type a table label, and the order is sent with the typed label. If the label matches a configured table's number, the result is the same as picking that table.
- Waiters can only view tables. Only admins can change them.

## Capabilities

### New Capabilities
- `table-management`: Admin maintenance of the restaurant's tables (create, edit, deactivate, delete; number, name, active state) and the waiter-facing list of selectable tables.

### Modified Capabilities
- `order-taking`: The "Create and send an order" requirement changes so the waiter picks the table from the configured list, with manual entry of a table label kept as a fallback.

Note: `order-taking` is introduced by the in-progress change `add-pos-ordering-system` and is not yet in `openspec/specs/`. This change builds on it and should be archived after that change.

## Impact

- **Backend** (`backend/src/main/java/ch/paynash/pos/`): a new `table` entity and migration, admin CRUD endpoints protected by the admin role (following `MenuAdminController`), and a read endpoint for waiters that returns the active tables. Sending keeps using `orders.table_label`, so sending, tickets, the order history, and the CSV export do not change.
- **App** (`app/lib/`): a new admin screen for tables next to the menu and staff screens (`app/lib/admin/`), and a table picker with a manual-entry fallback on the New order screen.
- **Tests**: backend tests for table CRUD, role checks, and the uniqueness of table numbers; widget tests for the admin screen and the picker.
- **Out of scope**: floor plans or table layouts, grouping tables into areas, seat counts, moving orders between tables, and QR codes on tables.
