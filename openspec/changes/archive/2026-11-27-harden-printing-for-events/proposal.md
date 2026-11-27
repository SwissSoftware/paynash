# Proposal

## Why

At a recent festival with 15 waiters, 2 ticket printers, and a self-ordering page, the POS in use (Paymash) printed some tickets twice and at other times printed nothing at all. The whole service at our festivals depends on production tickets reaching the bar and the kitchen. Our own system already prints from a central backend through a transactional outbox (`add-pos-ordering-system`, design D3). It still has gaps that matter in festival operation: failures are only visible to the waiter who sent the order, a paper change takes longer than the 60-second retry window, a backend crash during printing can print a ticket twice, and printers connected over WiFi make unclear outcomes much more frequent. The goal of this change is that **no ticket is lost or printed twice without someone noticing, whether the printer is on a cable or on WiFi**, so the system can carry our own festivals before it grows into a product.

## What Changes

- New **station** role and **print station view**: a tablet next to each printer, logged in for the whole event, shows that printer's tickets (time, ticket number, order number, table, waiter, status) and turns red with a sound when something needs attention.
- Station staff can reprint tickets and **resolve unclear tickets** ("was printed" or "reprint").
- Station staff can **cancel lines and void orders**, confirmed with a personal PIN and a reason. Each cancellation records who did it and at which station.
- **Waiting instead of failing**: when the printer certainly printed nothing (offline, paper end, cover open), the printer's queue waits without a time limit and continues by itself, in order, once the printer is ready. The station shows the problem while it lasts. **BREAKING** for the current behavior of marking such tickets `failed` after 60 seconds.
- **Crash-safe delivery**: a ticket that was being sent when the backend stopped is not sent again automatically after a restart. It is treated as unclear and shown at the station.
- **Running ticket number per printer** printed on every ticket, so staff can spot a missing or duplicated ticket in the pile.
- **Active printer monitoring**: the backend checks each printer's status regularly, even when no ticket is waiting, and shows offline, paper end, cover open, or paper near end (if the printer reports it) at the station.
- **Printers on WiFi** become a supported setup, with the same guarantees as wired printers.
- **Waiter app**: when sending fails, the message says clearly that sending again is safe and cannot create a second order.
- **Event setup guide** (docs): network layout (wired printers and server recommended, separate staff WiFi), printer WiFi settings, and a UPS for router, switch, server, and printers.
- **Load test at festival scale** (15 devices, peak load, printer and WiFi failures during service).

## Capabilities

### New Capabilities
- `print-station`: The per-printer station view, its live ticket list and alerts, resolving unclear tickets, and printer status monitoring shown to station staff.

### Modified Capabilities
- `ticket-printing`: Retry behavior changes from a 60-second window to waiting without a limit for failures where nothing printed; adds crash-safe delivery, the running ticket number on each ticket, printer status monitoring, and printers connected over WiFi.
- `staff-access`: Adds the `station` role, its permissions, and a session that lasts for a whole event.
- `order-taking`: Cancelling lines and voiding orders is also allowed from a station, with a personal PIN confirmation and an audit of who cancelled at which station.

Note: these three capabilities are introduced by the in-progress change `add-pos-ordering-system` and are not yet in `openspec/specs/`. This change builds on it and must be archived after it (and after `add-table-management`).

## Impact

- **Backend** (`backend/src/main/java/ch/paynash/pos/`): `printing` (worker states, queue waiting, ticket numbers, status polling, station endpoints), `security` (new role and permission matrix), `orders` (cancel and void from a station with PIN confirmation and audit). New Flyway migration after `V8__printing.sql`.
- **App** (`app/lib/`): new station screen (kiosk-style, stays awake), cancel/void from the station, clearer send error on the order screen.
- **Docs**: `docs/printing.md` (WiFi printers, station), a new event setup guide, `docs/acceptance.md` (load and failure tests).
- **Hardware**: one tablet per printer at the station. Findings from the hardware test of `add-pos-ordering-system` task 5.8 (printer status bits, print job status, behavior when WiFi drops while printing) feed into this change before its specs are written.
- **Out of scope**: cloud operation, multi-tenancy, and sync; guest self-ordering and payment; licensing; offline operation without the on-site backend; redundancy of the on-site backend; printer models other than Epson ePOS-Print printers. These follow as later changes (see `design.md`, Context).
