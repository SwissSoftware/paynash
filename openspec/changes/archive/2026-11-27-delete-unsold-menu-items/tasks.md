# Tasks

## 1. Delete items (backend)

- [x] 1.1 Add `MenuRepository.hasOrderLines(itemId)` (`EXISTS` on `order_line.menu_item_id`) and `MenuRepository.deleteItem(id)`. Verify through the service tests in 1.2.
- [x] 1.2 Add `MenuAdminService.deleteItem(id)` (design D1, D2): 404 for an unknown item, 409 with the item name and `alreadyOrdered: true` if it has order lines, otherwise delete it and register `deleteAfterCommitIfUnused(imageRef, null)`. Also map a `DataIntegrityViolationException` from the delete to the same 409. Verify in `ItemAdminTest` with the scenarios "Delete an item that was never ordered" (drink, image file gone), "Delete a service that was never ordered", "Delete an ordered item rejected" (item and order unchanged), and "Item ordered only in a voided order". Add one test that a shared image stays on disk while another item still uses it.
- [x] 1.3 Add `DELETE /admin/items/{id}` (204) to `MenuAdminController` with an OpenAPI summary, and change the description of `PUT /admin/items/{id}` to say that items which were never ordered can be deleted. Verify in `ItemAdminTest` that a waiter gets 403, and in `CategoryAdminTest` the scenario "Category empty after deletion" (delete the only unordered item, then the category delete returns 204).

## 2. Sending an order with a deleted item (backend)

- [x] 2.1 In `OrderService.send`, collect unknown item ids together with inactive items and reject with 409 and `inactiveMenuItemIds` containing both (design D3). The message names the inactive items and mentions deleted items. Verify in `SendOrderTest` with "Deleted item rejected" (no order saved, no print job, ids in `inactiveMenuItemIds`), and that the existing "Inactive item rejected" test still passes. Update any test that expected 400 for an unknown item.

## 3. App

- [x] 3.1 Add `MenuAdminApi.deleteItem(id)` (`DELETE /admin/items/$id`) and a delete handler in the fake backend (`app/test/support/fake_backend.dart`) that returns 409 with `alreadyOrdered` for items marked as ordered. Verify with the widget tests in 3.2.
- [x] 3.2 In `menu_admin_screen.dart`, add a per-item `PopupMenuButton` with "Delete" next to the type badge, a confirmation dialog, and the "already ordered" dialog with "Deactivate instead", which calls `updateItem` with the current values and `active: false`. Show only "OK" if the item is already inactive. Use `widget.terms` for labels (design D4). Verify in `menu_admin_test.dart`: deleting an unordered item removes it from the list; cancelling the confirmation keeps it; deleting an ordered item shows the dialog, and "Deactivate instead" marks it inactive.
- [x] 3.3 Verify in `orders_test.dart` that a send rejected with `inactiveMenuItemIds` for a deleted item removes that line and keeps the others. The existing handling should cover this, so add only a test unless it fails.

## 4. Docs and integration

- [x] 4.1 Copy the regenerated `backend/target/openapi.json` to `docs/api/openapi.json` and verify the diff shows the new `DELETE /admin/items/{id}` and the updated description.
- [ ] 4.2 Run `./mvnw verify` in `backend/` and `flutter test` and `flutter analyze` in `app/`, and verify all are green. Then, in a local installation, create a test item with an image in a new category, delete the item and then the category, and try to delete an item that was already ordered and use "Deactivate instead".

## Workflow follow-up

- Archive the change after review, so `menu-catalog` and `order-taking` get the updated requirements.
