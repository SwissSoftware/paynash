# Design

## Context

See proposal.md for why we need this. The relevant current state, from `add-pos-ordering-system`:

- `orders.table_label` is a free `VARCHAR(10)`. `OrderService.send` trims it, upper-cases it, and checks it against `[A-Za-z0-9]{1,10}`. There is no uniqueness per table: a table can have any number of sent orders, and sent orders are final.
- Tickets, the order history, and the CSV export read `table_label` directly from the order.
- `/admin/**` requires `ROLE_ADMIN`. Every other endpoint requires `ROLE_WAITER`, and admin inherits waiter through the role hierarchy. Repositories use `JdbcClient` with Flyway migrations (`V1`–`V6`).
- On the app's New order screen (`app/lib/orders/order_screen.dart`), the waiter builds the order on the device and types the table into the `table-input` `TextField` at the top of the draft panel. `POST /orders` sends the table label and all lines in one request. The screen polls the menu every 3 s (the `Polling` mixin), which also detects when the server is unreachable.

## Goals / Non-Goals

**Goals:**
- Add tables without touching the order model, tickets, history, or export.
- Keep sending on one code path whether the table was picked or typed.

**Non-Goals:**
- No database link between orders and tables. See D1.
- No custom sort order or drag-and-drop for tables. Tables are always sorted by number.

## Decisions

### D1: Tables are a lookup list, and orders keep only the label
A new table `dining_table (id, number VARCHAR(10), name VARCHAR(60) NULL, active BOOLEAN, created_at)` is added in `V7__dining_tables.sql`, with a unique index on `number`. It is called `dining_table` because `TABLE` is a reserved word, the same reason `orders` is plural. `orders` gets no `table_id` foreign key. Picking a table fills the draft's table label with the table's number, which is then sent as `tableLabel` with `POST /orders`.

*Why:* Deleting or renumbering a table then cannot change sent orders, manual entry keeps working unchanged, and sending, tickets, history, and export stay as they are.
*Alternative:* a nullable `orders.table_id` FK with `ON DELETE SET NULL`. Rejected because it adds two sources of truth (label and FK) for no behavior the specs need.

### D2: Numbers use the same normalization as order labels
Table numbers are trimmed, upper-cased, and validated with the same pattern as `OrderService`. The pattern and normalization move into a small shared helper (e.g. `TableLabels.normalize`) used by both services, so a table's number and the label of an order sent for it are always identical.

### D3: No checks against orders
Orders keep the label as text and are final once sent, so editing, deactivating, or deleting a table never looks at orders. There is nothing to lock and no 409 case.

### D4: Endpoints
- Admin (under `/admin`, so already admin-only): `GET /admin/tables` (all tables, including inactive), `POST /admin/tables`, `PUT /admin/tables/{id}` (number, name, active), `DELETE /admin/tables/{id}`. Deactivate and reactivate use `PUT` with `active`, the same as menu items.
- Waiter: `GET /tables` returns active tables with `{id, number, name}`.
- Errors use the existing `ApiException` / `Problems` format: validation errors on `number` (400), duplicate number (400 field error, so the form can show it on the field), unknown id (404).

### D5: Natural sort in the service
`GET /tables` sorts in Java with a comparator: purely numeric numbers come first in numeric order, then the rest in alphabetical order. A restaurant has tens of tables, so sorting in memory is cheaper to write and test than a SQL collation trick.

### D6: App UI
- **Admin:** a new `app/lib/admin/tables_screen.dart` with `tables_api.dart`, built like `staff_screen.dart`: a list with number, name, and an inactive badge, an add/edit dialog, an active switch, and delete with a confirmation dialog. It gets an entry in the admin navigation next to Menu and Staff.
- **Waiter:** in the draft panel of `order_screen.dart`, a wrapping row of table chips (number in large text, name below) is placed above the manual `table-input` field, which stays as the fallback. Tapping a chip selects that table and fills the field with its number; typing a number that matches a table highlights its chip. The table list is reloaded on the same 3 s poll as the menu.

## Risks / Trade-offs

- [A typed label and a configured table could drift apart, e.g. a waiter types "12 " or "t12"] → Both go through the same normalization (D2), so they match exactly.
- [A table is deleted or renumbered while it is selected in a waiter's draft] → The draft keeps the label text, and sending still works with that label, the same as manual entry. The chip simply disappears on the next poll.
- [Lots of tables make the waiter screen crowded on a phone] → Chips are compact and the row scrolls. Grouping by area is out of scope, so we will revisit it if a restaurant has more than about 40 tables.
- [Extra request on every poll] → `GET /tables` is one small indexed query, so the load is negligible next to the menu poll.

## Migration Plan

- `V7__dining_tables.sql` only adds a new table. No data changes and no backfill. The restaurant starts with an empty list, and waiters use manual entry until the admin adds tables.
- Rollback: deploy the previous build. The `dining_table` table can stay or be dropped, because nothing else references it.
- This change depends on `add-pos-ordering-system` and must be archived after it, because its `order-taking` delta modifies a requirement that change adds.
