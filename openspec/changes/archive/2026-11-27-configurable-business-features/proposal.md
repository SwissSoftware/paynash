# Proposal

## Why

Paynash knows exactly two purposes, hard-wired as the installation mode `event` or `salon`. The mode decides which functions exist (tables and stations vs. customers, invoices, and the sales report), which terms the apps use, and what an order points to (a table or a customer). It is checked in about 15 places in the backend (`@RequiresMode`, `settings.mode() == Mode.SALON`) and about 11 places in the app, and it can only be changed before the first order.

This blocks every business that does not fit one of the two profiles exactly, for example:

- a restaurant that needs tables *and* invoices for company customers,
- a café or bakery that sells over the counter without tables, customers, or kitchen tickets,
- a hairdresser or massage studio that wants the salon features plus a receipt printer but no invoices.

Each new target group would today need a new mode value and code changes at every mode check. Paynash should instead be configurable for many purposes from a set of building blocks.

## What Changes

- **BREAKING** The installation mode (`event` | `salon`) is replaced by a set of **features** that an admin switches on or off individually:
  - `tables`: tables and table administration,
  - `stations`: station accounts and the station view,
  - `customers`: customer management and history,
  - `invoices`: invoices, receipts, and A4 PDFs (requires `customers`),
  - `sales-report`: sales report and Excel export.

  Kitchen and bar tickets need no feature of their own: since `printer-category-routing`, a printer prints the categories assigned to it, in any configuration. A business without production tickets simply assigns no categories.
- **Business templates** set a sensible starting combination: Restaurant, Event, Salon, Counter sales (café, bakery, shop), and Custom. A template only fills in the features and terms. The admin can change each feature afterwards.
- **Terms** are part of the configuration instead of being tied to the salon mode: names for the `waiter` role, for menu items, and for sending an order (e.g. "Staff", "Services", "Confirm"). Templates bring defaults. Stored roles and states stay the same.
- **Order target** follows the features: an order can belong to a table, to a customer, to both, or to neither (counter sale). The current rule "exactly one of table or customer" is relaxed accordingly.
- **Switching features with data**: switching a feature on is always possible. Switching it off hides the function and rejects new requests for it, but keeps existing data, so history, invoices, and reports stay complete. The rule "the mode can only change before the first order" no longer applies.
- **Migration**: existing installations keep their behaviour. `event` becomes the Event template with `tables` and `stations`. `salon` becomes the Salon template with `customers`, `invoices`, `sales-report`, and the salon terms.
- The backend checks features instead of modes (replacing `@RequiresMode` and `ModeGate`), and requests for a switched-off feature are rejected with a clear message. The apps show only the functions of the enabled features.

Not in scope:
- Several businesses or locations in one installation (tenant model). One installation still serves one business.
- New business functions such as appointment booking, retail stock, or loyalty programmes. This change only makes the existing functions combinable.
- Free-text configuration of every label in the apps. Only the terms listed above are configurable.

## Capabilities

### New Capabilities
- `business-features`: The configurable features, their dependencies (e.g. `invoices` requires `customers`), business templates, configurable terms, and how switching features on and off treats existing data.

### Modified Capabilities
- `venue-mode`: The requirements "Installation mode", "Mode change only without orders", "Functions per mode", and "Salon terms" are removed and replaced by `business-features`. "Business details" and "Invoicing needs business details" refer to the `invoices` feature instead of salon mode. The VAT setting stays.
- `order-taking`: An order's target (table, customer, both, or none) depends on the enabled features instead of the mode. The salon-only order history becomes a history that shows customers and invoice status when those features are on.
- `ticket-printing`: Printer configuration and receipt status refer to features instead of venue modes.
- `customer-management`, `invoicing`, `sales-reporting`: Available when their feature is enabled, not in salon mode.
- `staff-access`: Permissions depend on the enabled features. Station accounts can log in only while `stations` is enabled.
- `menu-catalog`: Item types (food, drink, service) are available independent of a mode, so one menu can mix them.

`table-management` and `print-station` need no delta: their availability is covered by the feature gating in `business-features`.

Most of these capabilities exist so far only in changes that are not archived yet (`add-salon-mode`, `printer-category-routing`, `add-table-management`). The deltas of this change are written against their current versions.

## Impact

- Backend: `settings` package (`Mode`, `ModeGate`, `RequiresMode`, `SettingsService`, settings API), every controller and service with a mode check (`order`, `table`, `station`, `customer`, `invoice`, `report`, `menu`, `auth`), and a Flyway migration that converts the stored mode into features and terms and relaxes the orders check `orders_table_or_customer_check`.
- App: settings and setup screens (template choice, feature switches, terms), the home navigation, the order screen (order target), menu administration, and the station view, wherever they use `isSalon` or the mode today.
- API: The settings resource changes from `mode` to `features`, `template`, and `terms`. Clients that read `mode` must be updated at the same time (the app is the only client).
- Docs: `docs/event-setup.md` and `docs/salon-setup.md` become template guides, plus one for counter sales.
- Order of work: `add-salon-mode` should be archived before this change is applied. The in-flight printing changes (`harden-printing-for-events`, `printer-category-routing`, `fix-epos-http-version`) touch `ticket-printing` as well and should be finished or archived first to avoid conflicting deltas.
