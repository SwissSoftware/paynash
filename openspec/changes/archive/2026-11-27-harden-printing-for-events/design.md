# Design

## Context

See proposal.md for why. Relevant current state from `add-pos-ordering-system`:

- Printing uses a transactional outbox (`print_job`, `V8__printing.sql`) and one sequential worker per printer (`PrintWorker`, `EposClient`). Jobs are `pending`, `printed`, or `failed`. Errors where nothing can have printed (offline, paper end, cover open) are retried with 2/4/8/16 s pauses for `pos.printing.retry-window` (60 s), then marked `failed`. Unclear outcomes (timeout after sending, cutter or mechanical errors) are marked `failed` at once with "check the ticket and reprint if needed".
- `drain()` calls `startAttempt()`, sends to the printer, then `markPrinted()`. The job stays `pending` while it is sent. If the backend stops between sending and `markPrinted()`, the job is sent again after a restart.
- Failed tickets are shown only in the order history and the order (waiter and admin). Roles are `ADMIN > WAITER` (`SecurityConfig`). Cancel and void are admin-only under `/admin/**`. Sessions expire after at most 12 hours.

Event setup this change is designed for: a single router with one SIM as the internet uplink, switches, Aruba Instant On AP22 access points, printers, and the on-site backend all wired to the same switch. Only phones and tablets use WiFi. For later customers, printers may also be on WiFi.

Product direction agreed during exploration, which this design must not block: the POS becomes a cloud product sold as software only, built **local-first**. An on-site hub (today's backend, later installable on customer hardware) takes waiter orders and prints without internet. The cloud adds admin, sync, guest self-ordering with prepayment (cloud queue, picked up by the hub), and licensing. Those are later changes: cloud sync and multi-tenancy, then guest self-ordering, then licensing.

## Goals / Non-Goals

**Goals:**
- Every ticket ends in a state that someone has seen: printed, resolved by staff, or visibly waiting at the station.
- No ticket is printed twice automatically, including after a backend crash.
- Wired and WiFi printers use the same code path and the same guarantees.
- The station view and the station role work unchanged when the backend later runs as the on-site hub.

**Non-Goals:**
- Automatic failover to another printer.
- Push notifications. The station polls, like the order history.
- A native kiosk app. The station is a screen of the existing Flutter app.

## Decisions

### D1: Job states `pending -> printing -> printed | unclear | failed`
- `printing` (new): set and committed **before** the request goes to the printer.
- `unclear` (new): the printer may have printed. Causes: timeout after sending, broken connection after sending, cutter or mechanical error, or a job found in `printing` at startup. Unclear tickets never block the queue. They wait for a station decision (D5).
- `failed`: certainly not printed and not recoverable without a configuration change. Examples: no printer configured, device ID not found, ePOS-Print disabled, invalid print data.
- Errors where nothing printed (offline, paper end, cover open) keep the job `pending` with a `waiting_reason`. The pause grows to 16 s and repeats with **no time limit**. Later jobs of the same printer wait behind it, so the order is kept and the queue continues by itself. `pos.printing.retry-window` is removed.
- *Why:* splitting today's single `failed` into `unclear` and `failed` lets the station tell "check the pile" apart from "fix the configuration". Waiting instead of failing removes manual reprints after every paper change.
- *Alternative:* keep the 60 s window but make it configurable, e.g. 10 minutes. Rejected because any limit turns a long paper change into a batch of manual reprints.

### D2: Crash recovery
At startup, before the worker runs, every job in `printing` becomes `unclear` with "backend restarted while printing". It is never sent again automatically.

### D3: Running ticket number per printer
Each printer has a counter. The number is assigned when the job is created in the outbox transaction, so it follows queue order. It is printed large in the ticket header, e.g. `BAR 0142`. Cancellation tickets get numbers from the same counter. A reprint keeps the original number and adds the REPRINT banner. The station shows the last printed number and any unclear numbers, so staff can match them against the pile and notice gaps.
- *Alternative:* use the order number only. Rejected because one order produces tickets on several printers, and order numbers have gaps per printer.

### D4: Printer status monitoring
While a printer's queue is idle, the backend checks its status every ~10 s and keeps the last result in memory: `ready`, `offline`, `paper end`, `cover open`, or `paper near end`. Pending jobs, the station, and the admin printer list read from it.
- **Depends on the hardware test** (`add-pos-ordering-system` task 5.8). Preferred: an ePOS request that returns the printer's status bits without printing. Fallback: a TCP connect check, which can only detect `offline`. Paper near end requires the printer's near-end sensor.

### D5: Resolving unclear tickets
- **Manual (baseline):** the station shows the unclear ticket with its number. Staff check the pile and choose **Was printed** (status `printed`, `resolved_by` recorded) or **Reprint** (a new job with the REPRINT banner; the original becomes `printed`/resolved). The station alert stays on until every unclear ticket is resolved.
- **Automatic (if the hardware supports it):** if the printer reports whether a given print job ID was printed, the worker asks after an unclear outcome and resolves the ticket itself. Only tickets where the answer is still unknown go to staff. To be decided after the hardware test. The spec stays the same either way, because automatic resolution only reduces how many tickets reach the manual path.

### D6: Station role and session
- `ROLE_STATION` stands outside the `ADMIN > WAITER` hierarchy. A station account is a staff account with role `station`, configured with the printers it shows, e.g. "Station Bar" with printer "Bar".
- Allowed: the station ticket feed for its printers, reprint, resolving unclear tickets, and cancel and void with PIN confirmation (D7). Not allowed: sending orders, menu, tables, staff, printers, exports.
- Station sessions last 24 hours instead of 12, so one login covers an event day. Logout works as before.
- Station endpoints live under `/station/**`. Cancel and void get station variants that require PIN confirmation; the admin endpoints stay unchanged.

### D7: Cancel and void from a station with a personal PIN
A cancel or void request from a station carries the confirming person's username and PIN together with the reason. The backend checks the PIN with the same hashing and brute-force protection as login. The order line or order records `cancelled_by` = the confirming staff member and `cancelled_at_station` = the station account. The rest of the flow is unchanged: row lock, STORNO ticket, idempotency key.
- *Why:* the station stays logged in all evening and anyone at the bar can touch it. The PIN identifies who cancelled without logging the station out.
- **Any active staff account** (admin or waiter) can confirm a station cancel with its PIN, as decided with the user. Station accounts themselves cannot confirm. This deliberately gives waiters a way to cancel at the station, while the admin-only endpoints stay unchanged for waiters' own devices.

### D8: Station screen
- A new Flutter screen in `app/lib/station/`, shown after login with the station role. It polls `GET /station/tickets?printerId=...` every 2 s with ETag, in the same way as the order history (`add-pos-ordering-system` D4).
- Layout: printer status bar, alert banner, and the ticket list (time, ticket number, order number, table, waiter, line count, status), newest first, with today's tickets. Unclear tickets are pinned at the top with **Was printed** and **Reprint** buttons.
- The screen stays awake (wakelock). Browsers block sound until the user interacts, so the screen asks for one tap to "arm" alerts after login.
- Red banner plus a repeating sound when: the printer is not ready, a ticket is unclear, the oldest waiting ticket is older than 30 s, or the station has lost its connection to the backend.

### D9: WiFi printers
There is no separate code path. The guarantees come from D1–D5. `docs/printing.md` gets WiFi setup notes: fixed IP or DHCP reservation, WiFi power saving off, and signal strength checked at the printer's location. The event setup guide still recommends cables for printers and the server.

### D10: Clearer send failure in the waiter app
When `POST /orders` fails after the automatic retry, the order screen says that the order may or may not have arrived and that sending again is safe. Behind this, the existing idempotency key is kept until the draft changes, as today.

### D11: Load and failure test
The test uses a fake ePOS printer: a small HTTP server in the test sources that can answer slowly, drop connections before or after reading the request, or report paper end. A script with 15 simulated waiters sends orders at festival peak rate. Checks: every order has exactly one ticket per printer that is `printed`, or `unclear` and visible at the station; no ticket is sent twice; the queue continues after an outage. This is followed by an on-site run with the real printers and devices, recorded in `docs/acceptance.md`.

## Risks / Trade-offs

- [The station tablet sleeps, closes the tab, or loses WiFi, so alerts are missed] → Wakelock, kiosk/guided mode recommended in the guide, a "connection lost" alarm on the station itself, and the admin printer list shows when each station last polled.
- [A printer stays broken for a long time and its queue grows] → The station alarm escalates by waiting time. Moving waiting jobs to another printer is not part of this change (Open Questions); today an admin can deactivate the printer, activate another one, and reprint.
- [Waiting without a limit prints old tickets late, e.g. after a 20-minute outage] → Ticket numbers and times are on every ticket, and the station shows how long each ticket waited. Staff can cancel orders that are no longer wanted before the printer comes back.
- [Polling printer status adds traffic] → One small request per printer every ~10 s, and only while the queue is idle.
- [The PIN is typed on a shared screen] → The PIN field is masked and cleared after each use, and the same brute-force protection as login applies.
- [Hardware findings differ from assumptions] → D4 and D5 have baselines that work without the optional printer features.

## Migration Plan

- A new migration after `V8__printing.sql` adds the statuses `printing` and `unclear`, `waiting_reason`, resolution fields, the per-printer ticket counter and `ticket_no`, `cancelled_at_station` on lines and orders, and the `station` role with the station-to-printer assignment. Existing `failed` jobs stay `failed`.
- Rollout: deploy, create one station account per printer, log in on the station tablets, and run a test print from each station.
- Rollback: deploy the previous build after restoring the pre-update database backup, because the new statuses are unknown to the old code.
- Archive order: after `add-pos-ordering-system` and `add-table-management`.

## Open Questions

- **Hardware test results** (task 5.8, run later by the user): does ePOS-Print return status bits without printing, can print job status be queried, and how does the printer behave when WiFi drops while printing? The specs only require the baselines of D4 (offline is always detected; paper end, cover open, and paper near end are shown when the printer reports them) and D5 (manual resolution always works). The results only decide whether the optional variants are built, which is tracked as separate tasks.
- Does the ticket number reset per day (Europe/Zurich) or per event? This can be decided during implementation.
- Should waiting jobs move automatically when an admin swaps the active printer of a type? This is deferred. If it is wanted, it becomes a requirement in a follow-up change.
