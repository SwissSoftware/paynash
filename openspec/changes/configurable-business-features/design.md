# Design

## Context

See proposal.md for motivation and specs/ for the requirements. Current state relevant to the approach:

- `installation_settings` holds `mode` (`event` | `salon`) next to the business details and VAT. `SettingsService` caches the mode (`cachedMode`, reset after commit) and offers `lockModeForOrder()` (a `FOR SHARE` lock), so a mode change cannot race with the first order.
- Gating: `@RequiresMode` on 7 controllers (`TableController`, `TableAdminController`, `StationController`, `StationOrderController`, `CustomerController`, `InvoiceController`, `ReportController`), enforced by the `ModeGate` interceptor (409, `code: wrong_mode`). Inline checks in `OrderService`, `MenuAdminService`, `WaiterMenuController`, `SettingsService`, and `AuthService.login` (station accounts rejected in salon mode).
- Orders: `orders.table_label` and `orders.customer_id` are both nullable, with `orders_table_or_customer_check` requiring exactly one of them (V12).
- App: `VenueMode` in `settings_models.dart`, read from `GET /settings`. Navigation and salon terms are built from it in `home_shell.dart`, `settings_screen.dart`, `menu_admin_screen.dart`, `order_detail_screen.dart`, and `order_models.dart`.
- Flyway is at V15. The specs this change modifies exist so far only in unarchived changes (see D9).

## Goals / Non-Goals

**Goals:**
- One place that answers "is function X available?" for the backend and one for the app.
- Existing event and salon installations behave exactly as before after the migration.
- Adding a sixth feature later means one enum value, one column, and annotations, with no new checks spread over the code.

**Non-Goals:**
- Per-user or per-device feature sets. Features apply to the whole installation.
- Localising the whole app. Only the three terms are configurable.

## Decisions

**D1 Features as boolean columns on `installation_settings`, enum `Feature` in Java.**
Columns `feature_tables`, `feature_stations`, `feature_customers`, `feature_invoices`, `feature_sales_report`, plus `template` and `term_waiter`, `term_items`, `term_send`. The dependencies become database `CHECK`s (`NOT feature_invoices OR feature_customers`, `NOT feature_sales_report OR feature_invoices`), so no code path can store an invalid set. `SettingsService` validates first to give a readable message. Alternatives: a `features TEXT[]` column or a feature table. Both are more flexible but cannot express the dependencies as simple checks and are overkill for five fixed features.

**D2 `@RequiresFeature` and `FeatureGate` replace `@RequiresMode` and `ModeGate`.**
Same mechanism as today (handler interceptor, annotation on class or method), so the 7 controllers only swap the annotation: tables → `TABLES`, station controllers → `STATIONS`, customers → `CUSTOMERS`, invoices → `INVOICES`, report → `SALES_REPORT`. The answer stays 409 with `code: feature_disabled` and `feature: <name>`, and the message names the feature (e.g. "Invoices are switched off"). The inline mode checks become feature checks via `SettingsService.enabled(Feature)`. The cache is kept and reset after commit as today.

**D3 Order target rules in `OrderService`, database check dropped.**
V16 drops `orders_table_or_customer_check`. `OrderService.send` enforces the rules from the spec at send time: table label required if and only if `tables` is on; customer required if `customers` is on and `tables` is off, optional if both are on, forbidden if `customers` is off. `lockModeForOrder()` becomes `lockFeaturesForOrder()` and keeps the `FOR SHARE` lock, so a feature switch cannot interleave with a send and leave an order that breaks the rules of the moment it was saved. Old orders are never re-validated, which is what "Switching features with existing data" needs.

**D4 Templates are presets, stored for display only.**
`POST /admin/settings/template` writes the template's features and default terms in one transaction. The `template` column records the last applied template. Any later change to features or terms sets it to `custom`. Nothing in the code branches on the template, only on features, so templates can be added or changed without touching business logic. Contents: Event (`tables`, `stations`; terms Waiter, Items, Send), Restaurant (`tables`, `stations`, `customers`, `invoices`, `sales-report`; same terms), Salon (`customers`, `invoices`, `sales-report`; Staff, Services, Confirm), Counter sales (none; Staff, Items, Sell).

**D5 Settings API.**
`GET /settings` returns `features` (object of booleans), `template`, `terms`, and the business details. `PUT /admin/settings/features` takes the full feature object, `PUT /admin/settings/terms` the three terms. `PUT /admin/settings/mode` and the `mode` field are removed. The app is the only client and ships in the same release. The OpenAPI document (`OpenApiTest`) is updated with it.

**D6 Station sessions follow `stations`.**
`AuthService.login` rejects station accounts while `stations` is off (today: in salon mode). In addition, `TokenAuthenticationFilter` (via `AuthService.authenticate`) treats a station session as invalid while `stations` is off. Station sessions then get 401 on their next request and return to the login screen, as the spec requires, without deleting sessions when the feature is switched.

**D7 Menu item types are no longer tied to a mode.**
The checks in `MenuAdminService` and `WaiterMenuController` that reject `service` in event mode or non-services in salon mode are removed. All active items are offered when building an order. Whether a line prints a production ticket is decided by printer category routing only.

**D8 Migration V16.**
1. Add the feature, template, and term columns with defaults (all features off, terms Waiter/Items/Send).
2. `mode = 'event'` → template `event`, `feature_tables` and `feature_stations` on.
3. `mode = 'salon'` → template `salon`, `feature_customers`, `feature_invoices`, `feature_sales_report` on, terms Staff/Services/Confirm.
4. Add the dependency checks, drop `orders_table_or_customer_check`, drop `mode`.
Covered by an upgrade test in the style of `UpgradeMigrationTest`: one database migrated from an event installation and one from a salon installation, both with orders.

**D9 Spec bases come from unarchived changes.**
The deltas are written against the current versions in `add-salon-mode` (`venue-mode`, `order-taking`, `menu-catalog`, `customer-management`, `invoicing`, `sales-reporting`, `staff-access`) and `printer-category-routing` (`ticket-printing`). `openspec validate` already reports that archive would refuse them today. So before this change is archived, `add-pos-ordering-system`, `add-table-management`, `harden-printing-for-events`, `printer-category-routing`, and `add-salon-mode` must be archived in that order, and the deltas here must be checked against the resulting main specs (task 1.1).

**D10 No feature for production tickets.**
`printer-category-routing` already makes kitchen and bar tickets a matter of printer configuration: lines print where their category is assigned. A separate feature would duplicate that switch and could contradict it.

## Risks / Trade-offs

- [The base specs change again before this change is applied] → Task 1.1 re-checks every MODIFIED block against the archived main specs and adopts later wording before any code is written.
- [An admin switches off `customers`/`invoices` by mistake in the middle of the day] → Switching back restores everything (data is kept). The settings screen asks for confirmation before switching a feature off.
- [Apps with an old feature set keep showing hidden functions until they refresh] → The backend rejects those requests with `feature_disabled`, and the app reloads the settings when it receives that code.
- [Restaurant template contents are a product guess] → Templates are data in one place (D4) and can be adjusted without touching business logic.
- [Dropping `mode` breaks a rollback to the previous version] → Back up before the update as documented in `docs/operations.md`. A rollback restores the backup.
