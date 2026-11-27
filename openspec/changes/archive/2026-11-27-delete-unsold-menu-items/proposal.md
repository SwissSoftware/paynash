# Proposal

## Why

Admins can only deactivate menu items, never delete them (spec `menu-catalog`, "Deactivate instead of delete"). Items created by mistake, test items, and typos therefore stay in the admin list forever. They also keep their category from being deleted, because a category with any item in it, active or not, cannot be deleted. Categories already have a delete action, and items of every type (food, drink, service) should have one too.

Deactivation exists so past orders stay intact. An item that was never ordered has no past orders, so deleting it loses nothing.

## What Changes

- Admins can **delete a menu item that has never been ordered**. This works for every item type: food, drink, and service. The item, its image, and its place in the category disappear for good.
- An item that appears in at least one order line, even a cancelled line or one in a voided order, **cannot be deleted**. The system rejects the deletion and tells the admin to deactivate the item instead. Deactivation keeps working as before.
- The admin menu screen gets a "Delete" action per item, with a confirmation dialog. If the item was already ordered, the error dialog offers "Deactivate instead".
- Once its last item is deleted, a category becomes empty and can be deleted again with the existing category delete.
- If a waiter's draft still contains an item that was deleted in the meantime, sending is rejected the same way as for an inactive item: nothing is saved or printed, the app removes that line, refreshes the menu, and keeps the other lines. Today the same case fails with a generic validation error.

Not in scope:
- Deleting items that were already ordered (soft delete, hiding them from the admin list, anonymizing them).
- Deleting categories that still contain ordered items.
- Bulk deletion.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `menu-catalog`: "Deactivate instead of delete" becomes "delete only items that were never ordered, deactivate the others".
- `order-taking`: Sending an order whose draft contains a deleted item is rejected like an order with an inactive item.

## Impact

- Backend: `MenuAdminController` (new `DELETE /admin/items/{id}`), `MenuAdminService` (delete with the "never ordered" check and image cleanup after commit), `MenuRepository` (check whether an item has order lines, delete item), `OrderService.send` (report missing items as unavailable instead of a validation error). No migration: `order_line.menu_item_id ... ON DELETE RESTRICT` stays as the safety net.
- App: `menu_admin_api.dart` (`deleteItem`), `menu_admin_screen.dart` (per-item popup menu, confirmation, "Deactivate instead"). No change in `order_screen.dart` if the backend reuses `inactiveMenuItemIds`.
- Tests: `ItemAdminTest`, `SendOrderTest` in the backend; `menu_admin_test.dart` and the fake backend in `app/test/support/app_harness.dart`.
- API docs: the OpenAPI description of `PUT /admin/items/{id}` ("Items are deactivated instead of deleted") is updated.
