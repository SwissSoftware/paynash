# Tasks

## 1. Stack decision and project setup

- [x] 1.1 Confirm the technology stack with the user (design D1) and record the decision in design.md under D1. Verify that D1 names the chosen app, admin, backend, and DB technology
- [x] 1.2 Create the repository structure (`backend/` Maven/Spring Boot, `app/` Flutter with admin and waiter areas, `deploy/`) and verify that each part builds with its "hello world"
- [x] 1.3 Set up the Spring Boot backend skeleton with config (env/profiles), health endpoint, and JUnit + Testcontainers. Verify that `GET /health` returns 200 in a test
- [x] 1.4 Set up PostgreSQL via Docker Compose (dev) with Flyway (forward-only; rollback by restoring a backup, see design Migration Plan). Verify that a baseline migration applies to an empty database in a Testcontainers test
- [x] 1.5 Create the Flutter app skeleton for web with a configurable backend URL (CORS allowed for the dev origin). Verify that the app runs on localhost in the browser and calls `GET /health`
- [x] 1.6 Set up CI (lint, tests, build for the backend and the Flutter web app) and verify that a pipeline run is green

## 2. Staff access (spec: staff-access)

- [x] 2.1 Add a migration for `staff` (username unique, display name, role, active, pin_hash, failed_attempts, locked_until) and `session`. Verify the schema with a migration test
- [x] 2.2 Implement PIN hashing (Argon2id/bcrypt) and validation (4–8 digits). Verify with unit tests for valid and invalid PINs
- [x] 2.3 Implement the login endpoint with server-side sessions (12 h expiry), a generic error message, and lockout after 5 failures for 5 min. Verify with tests: success, wrong PIN, lockout, expired session
- [x] 2.4 Implement logout and session revocation when an account is deactivated. Verify with a test that the old token is rejected after deactivation
- [x] 2.5 Add auth/role middleware with a permission matrix (admin ⊇ waiter). Verify with tests: unauthenticated → 401, waiter on an admin route → 403
- [x] 2.6 Add admin endpoints for staff CRUD and PIN reset, plus a seed for the first admin with a one-time PIN. Verify with tests including duplicate username → validation error
- [x] 2.7 Build the app login screen, token storage behind an interface (browser storage on web), auto-logout on 401, and a logout button. Verify login/logout manually in the browser and with a widget test
- [x] 2.8 Build the admin UI staff management (list, create, deactivate, reset PIN). Verify through the UI that a newly created waiter can log in
- [x] 2.9 Document the API for auth/staff (e.g. OpenAPI) and verify that the spec file validates

## 3. Menu catalog and images (spec: menu-catalog)

- [x] 3.1 Add migrations for `category` and `menu_item` (type food/drink, price in Rappen, display order, active, image_ref). Verify with a migration test
- [x] 3.2 Add category CRUD with reordering and block deleting non-empty categories. Verify with tests including "delete non-empty → error with item names"
- [x] 3.3 Add item CRUD with validation (name ≤ 80, type required, price ≥ 0 with two decimals) and activate/deactivate instead of delete. Verify with tests for every validation rule
- [x] 3.4 Implement image upload: check magic bytes (JPEG/PNG/WebP), ≤ 5 MB, resize to WebP ≤ 512 px, store on a volume, serve with a content-hash URL and cache headers. Implement replace and remove. Verify with tests: valid JPEG, PDF rejected, 6 MB rejected, old image kept after a failed upload
- [x] 3.5 Add the waiter menu endpoint (active items only, grouped by category and sorted, with image URL and type), supporting ETag. Verify with tests: deactivated item missing, 304 when unchanged
- [x] 3.6 Build the admin UI for categories and items, including image upload and preview. Verify through the UI that an item with an image is created and shown
- [x] 3.7 Build the app menu view with image tiles, a placeholder with a food/drink icon, category filter, a food/drink visual marker, and an image cache. Verify with a widget test and a manual check in the browser

## 4. Order taking (spec: order-taking)

- [x] 4.1 Add migrations for `order`, `order_round`, and `order_line` (snapshot fields, status, version) plus a partial unique index for one open order per table. Verify with a test: a second open order for the same table fails at the DB level
- [x] 4.2 Add idempotency middleware (`Idempotency-Key`, 24 h storage) for the mutating order endpoints. Verify with a test: the same key twice → identical result, only one effect
- [x] 4.3 Implement "open order" (open or return the existing one), table label validation, and assigning the waiter. Verify with tests for both cases
- [x] 4.4 Implement adding, changing, and removing unsent lines with snapshot fields, qty 1–99, and a note ≤ 140 characters. Reject inactive items. Verify with tests including a price change after adding (snapshot stays)
- [x] 4.5 Implement "send round": mark unsent lines as sent, create a round, and in the same transaction create print jobs (hook for 5.2). Do nothing if there are no unsent lines. Verify with tests: first round, follow-up round prints only new lines, double request → one round
- [x] 4.6 Implement cancelling sent lines (reason required; status cancelled; excluded from total; creates a cancellation job). Verify with tests
- [x] 4.7 Add the open orders list (table, waiter, total, age) and admin void with a reason (waiter → 403). Verify with tests
- [x] 4.8 Handle concurrency: optimistic locking on state changes and parallel line additions. Verify with a concurrency test (two parallel additions → both lines exist)
- [x] 4.9 Build the app order screen: choose a table, tap tiles to add, change quantity and note, send button with status, show cancelling. Build the order overview with 3 s polling on visible screens and a "no connection" banner. Verify with widget tests and a manual test with two browser windows on the same table (sync ≤ 5 s)

## 4R. Rework order lifecycle (spec: order-taking; design D4)

Orders are no longer kept open per table: the waiter builds the order on the device and sends it in one go, after which it is final and shown in the order history. There is no payment. Tasks 4.1–4.9 built the original open-order flow and stay checked as history.

- [x] 4R.1 Add migration `V6__order_lifecycle.sql`: delete existing dev orders (no production data exists), drop the one-open-order-per-table index and `order_round` (with `order_line.round_id`), rename `opened_by`/`opened_at` to `sent_by`/`sent_at`, restrict order status to `sent|voided` and line status to `sent|cancelled`, and drop the line `version`. Verify with a migration test: several sent orders for one table are allowed, and status `open` and `unsent` are rejected
- [x] 4R.2 Replace the open/add/update/remove line and send-round endpoints with one `POST /orders` (table label, at least one line with menu item, qty 1–99, note ≤ 140) that validates everything, rejects the whole order with the list of inactive items, saves it as `sent` with snapshot fields, and creates the print jobs in the same transaction; idempotent via `Idempotency-Key`. Verify with tests: valid send, empty order → 400, invalid label → 400, inactive item → 409 naming it and nothing saved, price snapshot, two orders for the same table, double request → one order
- [x] 4R.3 Move "cancel line" to `/admin/orders/{id}/lines/{lineId}/cancel` and make void work on sent orders: void cancels all remaining lines and prints cancellation tickets; both lock the order row. Verify with tests: admin cancel, waiter → 403, cancel twice → 409, void prints STORNO only for lines not already cancelled, void twice → 409, cancel on voided order → 409
- [x] 4R.4 Replace the open orders list with the order history `GET /orders?date=YYYY-MM-DD` (default today in Europe/Zurich, newest first, with table, waiter, sent time, total, status) and keep `GET /orders/{id}` with lines and their status. Verify with tests: today only, other date, day boundary in local time, total excludes cancelled lines
- [x] 4R.5 Rework the app order screen: choose a table, build the draft locally (add tiles, change quantity and note, remove), send in one request with an idempotency key, reset after success, keep the draft after a failure, and drop inactive items named by the server. Verify with widget tests for send success, send failure, and inactive item
- [x] 4R.6 Replace the open orders overview with an order history screen (date picker, 3 s polling while visible, "no connection" banner) and an order detail view; admins see cancel line and void with a reason. Verify with widget tests for the list, the detail, and that waiters do not see cancel/void
- [x] 4R.7 Remove the obsolete order code and tests (rounds, unsent lines, open-order lookup, concurrency on open orders) and update the OpenAPI docs. Verify that the backend and app test suites pass and the OpenAPI test validates

## 5. Ticket printing (spec: ticket-printing)

- [x] 5.1 Add migrations for `printer` (name, address, device ID, type, active) and `print_job` (printer, content, kind normal/storno/reprint, status, attempts, last_error). Implement printer admin endpoints with the rule "exactly one active printer per type". Verify with tests
- [x] 5.2 Implement ticket rendering: split an order by type into at most one ticket per printer. Content: table, waiter, time, order number, qty × name, note; STORNO (single line and voided order, at most one per printer) and REPRINT variants. Verify with snapshot tests of the rendered tickets for a mixed order, drinks only, storno, void, and reprint
- [x] 5.3 Implement the ePOS-Print XML builder (text, double size, feed, `cut`) and the HTTP client against `/cgi-bin/epos/service.cgi`, including response parsing and error-code mapping (offline, paper end, cover open, ...). Verify with unit tests against recorded sample responses
- [x] 5.4 Implement the print worker: sequential per printer, retry with backoff ≥ 60 s, `printed`/`failed`, no automatic retry after confirmed printing or an unclear outcome. Verify with tests against a fake printer server (offline → failed after ≥ 60 s; success → exactly 1 request)
- [x] 5.5 Handle a missing printer for a type: the job fails immediately with "no printer configured" and the order is saved anyway. Verify with a test
- [x] 5.6 Add endpoints for test print and reprint (new job with the REPRINT flag) and expose ticket status per order for the app. Verify with tests
- [x] 5.7 Build the app UI: show print status per order in the order history, a failed-ticket warning with the error, and a reprint button. Build the admin UI: printer configuration and test print. Verify through the UI with a fake printer
- [ ] 5.8 Run a hardware test with a real Epson TM-m30III: enable ePOS-Print, run a test print, check umlauts (ä, ö, ü, é), the cut, and readability; test paper end and cover open. Verify with photos/notes of the test tickets in `docs/printing.md`, and adjust the code page/transliteration if needed
- [ ] 5.9 Document printer setup (static IP, enable ePOS, device ID) in `docs/printing.md` and verify that the steps can be reproduced on a reset printer

## 6. Payment (removed)

Removed: there is no payment in the app (see proposal). The section number is kept so later task numbers stay stable.

## 7. CSV export (spec: order-export)

- [x] 7.1 Implement the CSV writer: UTF-8 BOM, comma separator, RFC 4180 quoting, amounts with a dot and two decimals, ISO 8601 with offset (Europe/Zurich). Verify with unit tests for commas, quotes, line breaks in notes, and umlauts
- [x] 7.2 Add the export endpoint (admin only, `from`/`to` inclusive by local date, status filter `sent`/`voided`/all with default sent, filtered by `sent_at`, header-only when empty, filename with the date range, streaming). Verify with tests: one day, empty period, from > to → 400, waiter → 403, cancelled line → line_total 0.00
- [x] 7.3 Build the admin UI export dialog (date range, status) with download. Verify manually that the downloaded file opens in Excel with the columns intact and correct umlauts

## 8. Deployment and operations

- [x] 8.1 Create a production Docker Compose setup (backend, PostgreSQL, image volume, restart policies, HTTPS with a local hostname/certificate). Verify that `docker compose up` on a clean machine passes the health check over HTTPS
- [x] 8.2 Set up a daily DB and image backup with documented restore. Verify with a restore test on a second instance
- [ ] 8.3 Write the operations guide `docs/operations.md` (installation, first admin, printer setup, update, backup/restore, troubleshooting) and verify that a second person can install it using only the guide

## 9. End-to-end integration

- [ ] 9.1 Run the full flow on site with the web UI and real printers: admin creates items with images, two waiters in two browser sessions take a mixed order for the same table, the bar printer receives only drinks and the kitchen printer only food, a follow-up order for the same table is sent and printed, both orders appear in the order history, an admin cancels a line and the STORNO ticket is printed, and the CSV export contains all lines correctly. Record the result in `docs/acceptance.md`
- [ ] 9.2 Run the failure test on site: unplug the kitchen printer, send an order, see the failed status in the app, reconnect it, and reprint with "REPRINT". Verify that no ticket is lost or duplicated, and record the result in `docs/acceptance.md`

## 10. Later: mobile apps (Android and iOS)

- [ ] 10.1 Add the Android and iOS targets to the Flutter app with a configurable backend URL. Verify that the app starts on an Android emulator and an iOS simulator against the HTTPS backend from 8.1
- [ ] 10.2 Implement the token storage interface with secure storage (Keychain/Keystore). Verify login/logout manually on Android and iOS
- [ ] 10.3 Check the menu tiles, order screen, order history, and print status on both platforms, including the image cache. Verify manually on both platforms that an order sent on one device appears in the history on the other (≤ 5 s)
- [ ] 10.4 Configure the iOS build (NSLocalNetworkUsageDescription, ATS-compatible HTTPS) and Android release signing, and build for the distribution channel (TestFlight/internal track). Verify that the app installs from that channel on a real device
- [ ] 10.5 Repeat the on-site flow from 9.1 with a waiter on Android and a waiter on iOS, and record the result in `docs/acceptance.md`

## Workflow follow-up

- Archive the change after implementation and acceptance (`/opsx:archive`).
- Verify the archived specs under `openspec/specs/`.
