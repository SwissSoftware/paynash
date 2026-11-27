# Design

## Context

See proposal.md for the motivation and specs for the required behavior.

How it works today:
- `menu_item` rows are referenced by `order_line.menu_item_id ... ON DELETE RESTRICT` (V4). Nothing else references `menu_item`. Printer routing depends on categories (`printer_category`), not on items.
- `category.id` is referenced by `menu_item.category_id ... ON DELETE RESTRICT`. `MenuAdminService.deleteCategory` rejects a category that has any item, active or inactive, and returns a 409 that lists the item names.
- Order lines keep their own copy of name, type, category name, unit price, and VAT rate. Reports, exports, and invoices read those copies, so an item that was never ordered is not needed by any of them.
- Images are shared by reference (`image_ref`). `deleteAfterCommitIfUnused` removes a file after commit once no item uses it any more.
- `OrderService.send` loads each item with `menu.findItem`. A missing item becomes a 400 validation error ("Menu item N does not exist"). An inactive item becomes a 409 with `inactiveMenuItemIds`, and `order_screen.dart` reacts by removing those lines and reloading the menu.

## Goals / Non-Goals

**Goals:**
- A hard delete that can never break an existing order, with the existing FK as the safety net.
- Reuse the existing patterns: the category-delete conflict, the image cleanup after commit, and `inactiveMenuItemIds` on send.

**Non-Goals:**
- No schema change and no migration.
- No new `sold` field in the admin item list (see D4).

## Decisions

### D1: Hard delete, guarded by an explicit check and the FK
`DELETE /admin/items/{id}` (admin only, like the other `/admin` endpoints) runs in one transaction:
1. Load the item (404 if it does not exist).
2. `SELECT EXISTS (SELECT 1 FROM order_line WHERE menu_item_id = ?)`. If true, throw `ApiException.conflict("<name> was already ordered and cannot be deleted. Deactivate it instead.")` with the detail `alreadyOrdered: true`.
3. `DELETE FROM menu_item WHERE id = ?`.
4. Register `deleteAfterCommitIfUnused(item.imageRef(), null)`.
5. Return 204.

We check explicitly so the admin gets a clear message with the item's name. Relying only on the FK violation would give a generic error. The check counts every order line, whatever the status of the line or the order, which matches the spec ("including cancelled lines and lines of voided orders").

Alternative considered: soft delete with a `deleted` flag. Rejected for now (decision in explore: option A). It would need a migration and filters in every item query, and it would also have to solve deleting categories that contain soft-deleted items.

### D2: The FK violation from a race also becomes a 409
If an order line for the item is inserted between step 2 and step 3, the `DELETE` fails because of the RESTRICT FK. `deleteItem` catches `DataIntegrityViolationException` and throws the same conflict as in step 2. The opposite race (the delete commits first, then a send inserts a line) makes the send fail on the FK. This is extremely rare, needs an admin and a waiter acting at the same moment, and resending gives the clean D3 response. We accept it rather than locking menu rows during every send.

### D3: Deleted items on send reuse `inactiveMenuItemIds`
In `OrderService.send`, an item id that does not exist is collected next to the inactive items instead of failing at once. The 409 then lists the ids of inactive and deleted items in `inactiveMenuItemIds`. The message names the inactive items it knows and adds "and items that were deleted" when there are deleted ones. The app already removes these lines and reloads the menu, so `order_screen.dart` needs no change.

Alternative considered: a separate `deletedMenuItemIds` key. Rejected: the app treats both cases the same way, and a second key would only add client code.

Note: this changes a missing-item response from 400 to 409. Only the app uses this API, and it treats a 400 for an unknown item as a generic error, so nothing relies on the 400.

### D4: The UI always shows "Delete" and reacts to the 409
Each item row in `menu_admin_screen.dart` gets a trailing `PopupMenuButton` with "Delete", next to the type badge, like the category rows. Choosing "Delete" opens a confirmation dialog ("Delete <name>?", following the customer delete dialog). If the response is a 409 with `alreadyOrdered`, the app shows a dialog with the message and two actions: "Cancel" and "Deactivate instead". "Deactivate instead" calls `updateItem` with the item's current values and `active: false`. If the item is already inactive, only "OK" is shown. The labels use `widget.terms` so salons see "service".

Alternative considered: hide "Delete" for items that were already ordered. That needs a `sold`/`ordered` flag in `GET /admin/items`, which means an extra query per list load, and it hides why the action is missing. Rejected.

### D5: No reordering after delete
`display_order` values may have gaps after a delete. Sorting still works, and `reorderItems` assigns fresh indices on the next drag. We do not renumber.

## Risks / Trade-offs

- [An admin deletes a correct item by mistake] → Confirmation dialog. Only items that were never ordered can be deleted, so no history is lost, and the admin can create the item again.
- [Categories that contain ordered items still cannot be deleted] → Accepted limitation of option A, stated in the proposal. Admins deactivate those items instead.
- [Shared image file removed while still in use] → `deleteAfterCommitIfUnused` counts the remaining users after commit and keeps the file if another item uses it.
- [400 → 409 for unknown items on send] → Only our own app uses this API (see D3).

## Migration Plan

No data migration. Deploy backend and app together as usual. On rollback, the old backend has no delete endpoint, and items that were already deleted stay deleted. They were never ordered, so no data is lost.
