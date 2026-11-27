# Tasks

## 1. Backend: tables (spec: table-management)

- [x] 1.1 Add `V7__dining_tables.sql` with `dining_table` (number VARCHAR(10), name VARCHAR(60) NULL, active, created_at) and a case-insensitive unique index on `number`. Verify with a schema test that a duplicate number with different case fails at the DB level
- [x] 1.2 Move table label normalization and validation into a shared `TableLabels` helper and use it in `OrderService.send` (design D2). Verify that the existing `SendOrderTest` still passes
- [x] 1.3 Implement `TableRepository`, `TableService`, and `TableAdminController` (`GET/POST /admin/tables`, `PUT/DELETE /admin/tables/{id}`) with number and name validation, upper-case numbers, duplicate numbers as a field error, and 404 for unknown ids. Verify with tests: create with and without name, invalid numbers, duplicates (also different case and inactive), rename, renumber keeps sent orders, deactivate/reactivate, delete keeps sent orders and frees the number
- [x] 1.4 Implement `GET /tables` for waiters with active tables only, in natural order (design D5). Verify with tests: inactive table hidden, "2" before "10" before "T1", waiter gets 403 on admin endpoints, no session gets 401
- [x] 1.5 Document the endpoints in OpenAPI and verify that `OpenApiTest` lists `/tables`, `/admin/tables`, and `/admin/tables/{id}`

## 2. App: admin table screen

- [x] 2.1 Add `app/lib/admin/tables_api.dart` and `tables_screen.dart` (list with number, name and inactive marker; add/edit dialog with number, name and active switch; delete with confirmation) and a "Tables" admin section in `home_shell.dart`. Verify with widget tests: create a table, duplicate number shows the field error, deactivate, delete after confirming, and waiters do not see the section

## 3. App: table picker on the New order screen (spec: order-taking)

- [x] 3.1 Load active tables on the New order screen together with the menu poll and show them as chips above `table-input`. Tapping a chip fills the table field; a typed number that matches a table highlights its chip; manual entry stays the fallback. Verify with widget tests: pick a table and send (label is the table number), typed label without a table is sent unchanged, no tables shows only the field, a table deactivated by an admin disappears on the next poll

## 4. Integration

- [x] 4.1 Run the full backend test suite and `flutter analyze` plus `flutter test` and verify that everything passes

## Workflow follow-up

- Archive `add-pos-ordering-system` first, then this change, because the `order-taking` delta modifies a requirement that change adds.
