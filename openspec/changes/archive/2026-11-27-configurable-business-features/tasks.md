# Tasks

## 1. Prerequisites

- [x] 1.1 Confirm that `add-pos-ordering-system`, `add-table-management`, `harden-printing-for-events`, `printer-category-routing`, and `add-salon-mode` are archived (design D9). Compare every MODIFIED block in `specs/` with the resulting main specs, adopt any later wording, and verify that `openspec validate configurable-business-features --strict` no longer reports "Archive would refuse".

## 2. Data model and settings (backend)

- [x] 2.1 Add enum `Feature` and migration V16 (design D1, D8): feature, template, and term columns, conversion from `mode`, dependency checks, drop `orders_table_or_customer_check` and `mode`. Add an upgrade test that migrates an event and a salon installation with orders and verify features, template, terms, and orders match the spec "Existing installations keep their behaviour".
- [x] 2.2 Rework `Settings`, `SettingsRepository`, and `SettingsService`: `enabled(Feature)`, `features()`, `terms()`, `lockFeaturesForOrder()`, dependency validation with readable messages, template application, and `custom` after manual changes (design D4). Verify with tests for every scenario of "Feature dependencies" and "Business templates".
- [x] 2.3 Replace the settings API (design D5): `GET /settings` with `features`, `template`, `terms`; `PUT /admin/settings/features`, `PUT /admin/settings/terms`, `POST /admin/settings/template`; remove `PUT /admin/settings/mode`. Verify with controller tests (admin only, waiter forbidden, term validation 1 to 30 characters) and an updated `OpenApiTest`.

## 3. Feature gating (backend)

- [x] 3.1 Replace `@RequiresMode`/`ModeGate` with `@RequiresFeature`/`FeatureGate` (design D2) on the 7 controllers, answering 409 with `code: feature_disabled`. Verify with one test per feature that a request is rejected while the feature is off and accepted while it is on.
- [x] 3.2 Order target rules in `OrderService` (design D3): table label iff `tables`, customer required/optional/forbidden per the spec. Verify with tests for every scenario of "Create and send an order" and "Confirm an order for a customer", including counter sale and table order with optional customer.
- [x] 3.3 Remove the item-type mode checks in `MenuAdminService` and `WaiterMenuController` (design D7). Verify that a menu with services and drinks is accepted and offered, and that the existing menu tests pass.
- [x] 3.4 Invoicing, customers, and report: replace mode wording and checks with features, and reject invoices for orders without customer with the message from the spec. Verify with the scenarios of `invoicing`, `customer-management`, `sales-reporting`, and "Invoicing needs business details".
- [x] 3.5 Station sessions (design D6): reject station login and station requests while `stations` is off. Verify that a logged-in station gets 401 after the feature is switched off and can log in again after it is switched on.
- [x] 3.6 Data kept when switching off: verify with tests that history keeps table labels and customers after `tables`/`customers` are switched off, and that invoices are listed again with gapless numbering after `invoices` is switched off and on.

## 4. App

- [x] 4.1 Replace `VenueMode` with features, template, and terms in `settings_models.dart` and `settings_api.dart`, and reload the settings when a response has `code: feature_disabled`. Update `settings_test.dart`.
- [x] 4.2 Settings screen: template choice, feature switches with dependency hints and a confirmation before switching off, and term fields. Verify with widget tests.
- [x] 4.3 Navigation and screens follow the features and terms (`home_shell.dart`, `staff_screen.dart`, `menu_admin_screen.dart`, `order_models.dart`, `order_detail_screen.dart`, order screen target selection). Verify with widget tests for Event, Salon, Counter sales, and Restaurant (table plus optional customer), and update `salon_orders_test.dart`, `tables_test.dart`, `station_test.dart`, `invoices_test.dart`, `customers_test.dart`, and `report_test.dart`.
- [x] 4.4 Printer screen shows the "receipts" switch only while `invoices` is on. Verify with `printing_test.dart`.

## 5. Docs and integration

- [x] 5.1 Turn `docs/event-setup.md` and `docs/salon-setup.md` into template guides and add `docs/counter-setup.md` (Counter sales). Verify every described setting exists in the app.
- [x] 5.2 Run `./mvnw verify` in `backend/` (JDK 21) and `flutter test` in `app/` and verify both are green. Then apply each template once in a local installation and verify the visible functions match the template table in design D4.
