# Design

## Context

This is a greenfield project: there is no code yet, and the repository contains only the OpenSpec setup. For motivation and scope, see `proposal.md`. For behavior, see the specs under `specs/`.

These constraints shape the design:
- Target: waiters use their own or the restaurant's Android and iOS phones on the restaurant WiFi.
- Phasing (decided with the user): phase 1 delivers the backend and a **web UI running on localhost**. The Android and iOS apps follow in a later phase, built from the same Flutter codebase (see D1 and tasks section 10).
- The two Epson TM-m30III printers (bar = `drink`, kitchen = `food`) sit on the restaurant LAN. The backend prints to them over **Epson ePOS-Print (XML over HTTP/HTTPS)**, as decided with the user. The component that prints must therefore be able to reach the printers' LAN addresses.
- The currency is CHF and the time zone is Europe/Zurich.
- The technology stack is decided in D1 (Java/Spring Boot backend, Flutter UI). All other decisions are independent of the stack.

## Goals / Non-Goals

**Goals:**
- One central backend owns all data, rules, and print delivery. Clients are thin.
- Taking an order and sending it never waits on a printer, and is never lost because of a printer problem.
- Behavior is safe when the WiFi is unreliable: requests that change data can be retried (idempotent).
- Running it is simple: one small server on site.

**Non-Goals:**
- Offline order taking without a connection to the backend. The app shows "no connection" and blocks sending.
- Multiple locations, multiple tenants, or cloud hosting in v1.
- Using the Epson SDK on the device (direct printing from the phone was rejected).
- Card-terminal integration, fiscal receipts, and split payments.

## Decisions

### D1 – Technology stack (decided)
| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4 (4.1.x; Boot 3 is out of open-source support), Maven, JDBC via `JdbcClient` |
| Database | PostgreSQL, migrations with Flyway |
| Auth | Spring Security (custom bearer-token filter for server-side sessions, see D7) |
| API docs | springdoc-openapi (OpenAPI 3) |
| Backend tests | JUnit 5, Spring Boot Test, Testcontainers (real PostgreSQL) |
| UI (waiter + admin) | Flutter, built for **web** in phase 1 and served on localhost |
| Mobile apps (later phase) | Flutter for Android and iOS, from the same codebase |

- *Why Java/Spring Boot*: Java is the language the user knows best. Spring Boot covers REST, validation, security, transactions, scheduling (print worker), and the Java HTTP client covers ePOS-Print over XML/HTTP.
- *Why Flutter, web first*: the user wants a UI on localhost now and the same functionality in Android and iOS apps later. Flutter builds web, Android, and iOS from one codebase, so the later step is mostly packaging and platform configuration, not a rewrite. Dart is close to Java in syntax and typing.
- *Keeping the mobile step cheap*: all rules live in the backend; the UI only calls the documented API. Platform-specific code (token storage, network permissions) stays behind small interfaces in the app.
- *Rejected*: a server-rendered Java UI (Thymeleaf, Vaadin) cannot be reused in the mobile apps. React + Capacitor/React Native shares less code with the apps than Flutter. A PWA was rejected because install, session, and app-store distribution on iOS are weaker.

### D2 – Deployment: on-site server in the restaurant LAN
The backend, database, and image storage run as containers (Docker Compose) on a small on-site machine, such as a mini PC or NAS, in the same LAN as the printers.
- *Why*: ePOS-Print needs direct LAN access to the printers. On site, the system keeps working when the internet is down.
- *Phase 1*: during development the backend runs locally (Spring Boot on localhost, PostgreSQL in Docker Compose). The on-site Compose setup is created in tasks section 8.
- *Alternative*: a cloud backend with a local "print bridge" agent. This is more complex (two deployables, a tunnel) and depends on the internet connection. It stays an option for later (Epson Server Direct Print would also allow it).

### D3 – Printing via a transactional outbox and per-printer worker
When a round is sent or a line is cancelled, the same DB transaction writes one `print_job` row per affected printer, with the rendered ticket content, `status = pending`, and `attempts = 0`. A background worker processes jobs **sequentially per printer**:
1. It builds an ePOS-Print XML document (`<epos-print>`: text with double size for the table/header, lines, feed, `<cut type="feed"/>`).
2. It sends `POST http(s)://<printer>/cgi-bin/epos/service.cgi?devid=<deviceId>&timeout=<ms>` with a SOAP envelope.
3. It parses the response's `success` and `code` attributes and maps the codes, for example `EPTR_REC_EMPTY` → paper end, `EPTR_COVER_OPEN` → cover open, and timeout or connection refused → offline.
4. On success it sets `printed`. On error it retries with backoff (e.g. 2s, 4s, 8s, ... up to ≥60s in total), then sets `failed` with the error.
- *Why*: saving the order never depends on the printer (spec: printing must not block saving). Jobs survive restarts. Sequential processing per printer keeps ticket order intact.
- *Duplicate protection*: a job is only retried while it is not `printed`. An unclear outcome, such as a timeout after the data was sent, is treated as `failed` rather than retried automatically, so tickets are not duplicated; staff decide with "Reprint". This is a deliberate trade-off.
- *Reprint* creates a new job with the same content and the "REPRINT" flag.
- *Encoding*: German umlauts must print correctly. Use the `lang`/code page settings of the ePOS text element and verify with a test ticket (see Risks).

### D4 – Order model and concurrency
- `order` (id, table_label, status `open|paid|voided`, opened_by, opened_at, version)
- `order_line` (id, order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot, unit_price_snapshot, qty, note, status `unsent|sent|cancelled`, round_id, cancel_reason)
- `order_round` (id, order_id, number, sent_by, sent_at)
- A partial unique index on `order(table_label) WHERE status = 'open'` enforces at most one open order per table.
- Adding a line inserts a new row, so concurrent additions do not conflict. State changes (send, settle, void) use optimistic locking on `order.version` or `SELECT ... FOR UPDATE`.
- Clients refresh the open order and the order list by **polling every 3 seconds** while the screen is visible. This is simpler and more robust on WiFi than WebSockets, and it meets the 5-second requirement. SSE can be added later.

### D5 – Idempotency
The client sends an `Idempotency-Key` (UUID) header for "send round", "settle", "cancel line", and "add line". The server stores key → result for 24 hours and returns the stored result for repeated requests. This covers double-taps and network retries (spec: send and settle).

### D6 – Money as integer Rappen
All amounts are stored and calculated as integers (Rappen). They are formatted to `0.00` only at the API/CSV boundary. This avoids floating-point errors.

### D7 – Authentication: username + PIN, server-side sessions
- PINs are hashed with Argon2id (or bcrypt).
- After login the client receives an opaque, random session token (bearer) that is stored server-side with `expires_at` = login + 12 h. Deactivating an account deletes all its sessions immediately. This is the reason for server-side sessions instead of JWT.
- Web (phase 1): the token is kept in browser storage of the Flutter web app. Mobile (later phase): the token is stored in secure storage (Keychain/Keystore). Both sit behind one token-storage interface in the app.
- Lockout: a failed-attempt counter per account; 5 failures → `locked_until = now + 5 min`.
- Role checks are enforced centrally in the API layer (a Spring Security filter plus method/route rules) and checked per route against the permission matrix in the `staff-access` spec.

### D8 – Images
The upload goes to the backend (multipart). The backend validates the MIME type by magic bytes (JPEG/PNG/WebP) and the size (≤ 5 MB). It creates a display version (e.g. max 512 px, WebP) and stores it on a volume, served with long cache headers and a content hash in the URL. The original is not kept, which saves space. The app caches images locally (the browser cache in phase 1).
- *Alternative*: S3/MinIO. This is unnecessary for one location; it can be added later behind a storage interface.

### D9 – CSV export
A server-side streaming endpoint (`GET /exports/orders.csv?from&to&status`) writes a UTF-8 BOM and RFC 4180 escaping, with columns exactly as in the `order-export` spec. Date filtering on `opened_at` uses Europe/Zurich. The export reads from the snapshot fields of the lines, so later menu changes do not alter the result.

### D10 – Admin UI
Admin functions (menu, categories, images, staff, printers, test print, export, void/reopen) are the admin area of the same Flutter web app, shown only to the admin role. *Why web*: image upload and CSV download are much more convenient on a desktop. Admins can still take orders in the waiter screens (web now, mobile app later), because the admin role includes all waiter permissions.

### D11 – Transport security
In phase 1 the web UI and backend run on localhost over plain HTTP, which is acceptable because nothing leaves the machine. HTTPS is introduced with the on-site deployment (task 8.1) and is required before any device other than localhost, including the mobile apps, connects. From then on the app talks to the backend over HTTPS, using a local hostname (e.g. `pos.local` or a real subdomain with a DNS-01 certificate pointing to the LAN IP). This avoids iOS App Transport Security exceptions and sending PINs in plain text. The iOS app declares `NSLocalNetworkUsageDescription`. ePOS to the printers uses HTTPS where the printer supports it, otherwise HTTP within the closed LAN.

## Risks / Trade-offs

- [Unreliable WiFi in the dining room] → Idempotency keys (D5), a clear "no connection" banner, and the send button shows its state. A WiFi site survey is recommended before go-live.
- [A printer timeout after the data was sent could cause a double print or a missing ticket] → Do not retry automatically when the outcome is unclear; mark as `failed` and show it prominently with a reprint button (D3).
- [Umlauts or special characters print incorrectly] → An early end-to-end test with the real TM-m30III (task 5.x), and fall back to transliteration if needed.
- [The on-site server is a single point of failure] → Automatic container restart, a daily DB backup to external storage, and documented restore steps.
- [Polling load with many devices] → The number of devices is small (< 20). Poll only on visible screens; ETag/`If-None-Match` prevents unnecessary data transfer.
- [Mobile apps come later, so platform issues (iOS local network permission, ATS, secure storage) surface late] → Keep platform-specific code behind small interfaces, keep all rules in the backend, and run the Flutter app on Android/iOS early in the mobile phase (tasks section 10).
- [Flutter Web is less polished than a classic web app for admin forms and file upload] → Acceptable for an internal admin UI on a desktop; image upload and CSV download are checked through the UI in tasks 3.6 and 7.3.
- [Legal/fiscal requirements for a POS (e.g. tamper-proof records)] → Out of scope for v1. Reopening and cancelling do not delete history (audit fields). Check before productive use.

## Migration Plan

This is a new installation, so no data migration is needed.
1. Set up the on-site server (Docker, Compose, TLS certificate, backup job).
2. Give the printers static IPs or DHCP reservations, enable ePOS-Print in the printers' web config, and note the device ID (default `local_printer`).
3. Deploy the backend and run the DB migrations. A seed creates the first admin account with a one-time PIN.
4. Admin: enter printers and run a test print, then enter categories, items, and images, then create waiter accounts.
5. Phase 1: staff use the web UI. Later phase: distribute the app (Android: internal track/APK/MDM; iOS: TestFlight or Apple Business Manager/custom app).
6. Rollback: the system is new, so go back to the manual process. Back up the DB before every update so the previous container version can be restored.

## Open Questions

- Exact layout of the production tickets (font size, logo yes/no). This can be adjusted after the first test print without changing the specs.
- App distribution channel for iOS (TestFlight vs. Apple Business Manager), to be decided in the mobile phase. This depends on the number of devices and on whether the devices belong to the business.
- Backup target (NAS, USB, or cloud storage).
