# Tasks

## 1. Data model

- [ ] 1.1 Add `V9__print_hardening.sql`: job statuses `printing` and `unclear`, `print_job.waiting_reason`, `ticket_no`, `resolved_at`, `resolved_by_station`; a per-printer ticket counter; `cancelled_by` / `cancelled_at_station` on `order_line` and `voided_at_station` on `orders`; role `station` and a `station_printer` assignment table. Verify with a migration test on Testcontainers that existing `failed` jobs stay `failed` and the new constraints accept the new values

## 2. Backend: delivery states and waiting (spec: ticket-printing; design D1, D2)

- [ ] 2.1 Set jobs to `printing` in a committed update before `EposClient.print` and to `printed` / `unclear` / `failed` afterwards. Split `EposClient` results into "nothing printed" (offline, paper end, cover open), "unclear", and "failed" (configuration errors). Verify with `EposClientTest` that every mapped code lands in the expected category
- [ ] 2.2 Replace the retry window with waiting without a time limit: "nothing printed" keeps the job `pending` with `waiting_reason` and a pause of at most 16 s; later jobs of the same printer wait behind it. Remove `pos.printing.retry-window`. Verify with `PrintWorkerTest`: a 4-minute simulated paper end prints all queued tickets in order afterwards, none becomes failed, and the other printer is not delayed
- [ ] 2.3 Mark jobs left in `printing` as `unclear` ("system restarted while printing") at startup, before the worker runs. Verify with a test that a job in `printing` at startup is `unclear` and the fake printer receives nothing
- [ ] 2.4 Update `docs/printing.md` ("How printing behaves", troubleshooting table) for waiting, `unclear`, and `failed`. Verify that the documented settings match `application.properties`

## 3. Backend: ticket numbers (spec: ticket-printing; design D3)

- [ ] 3.1 Assign the next per-printer number in the outbox transaction for order, cancellation, and reprint jobs (reprints keep the original number), and print it large in the header via `TicketRenderer`. Verify with `TicketRenderingTest` (header shows "BAR 0142") and a test that two concurrent order sends get distinct, consecutive bar numbers

## 4. Backend: printer status monitoring (spec: ticket-printing, print-station; design D4)

- [ ] 4.1 Add a status probe that runs at least every 15 s per active printer while its queue is idle and keeps the latest status in memory (baseline: offline via connect check). Expose it in the admin printer list. Verify with a test against a fake ePOS server that stopping the server shows `offline` within 15 s and starting it shows `ready`
- [ ] 4.2 After the hardware test (`add-pos-ordering-system` 5.8): if ePOS returns status bits without printing, report paper end, cover open, and paper near end from the probe; otherwise record in `docs/printing.md` that only offline is detected while idle. Verify against the real TM-m30III and record the result in `docs/printing.md`

## 5. Backend: station role and endpoints (spec: staff-access, print-station; design D6)

- [ ] 5.1 Add role `station` outside the `ADMIN > WAITER` hierarchy, printer assignment in `StaffAdminController` (required for stations), and a 24-hour session lifetime for stations. Verify with tests: station without printer is rejected, station session valid after 13 h and expired after 24 h, waiter session still expires after 12 h
- [ ] 5.2 Implement `GET /station/tickets?printerId=` (today's tickets of an assigned printer, newest first, unclear pinned on top, with ETag) and `GET /station/printers` (assigned printers with status, last printed ticket number, number waiting, oldest waiting age). Verify with tests: bar station sees only drink tickets, 403 for an unassigned printer, 403 for station on `/orders`, `/admin/**`, `/exports/**`, and 304 for an unchanged list
- [ ] 5.3 Implement `POST /station/tickets/{id}/resolve` with `printed` or `reprint`, idempotent and locked so a second resolution has no effect. Allow station reprint of tickets of its printers. Verify with tests: "printed" prints nothing, "reprint" prints once with REPRINT and the original number, two concurrent resolutions cause at most one reprint
- [ ] 5.4 Record the last poll time per station and show it in the admin printer list. Verify with a test that the timestamp updates on each station poll
- [ ] 5.5 Document the station endpoints in OpenAPI and verify that `OpenApiTest` lists them

## 6. Backend: cancel and void from a station (spec: order-taking; design D7)

- [ ] 6.1 Implement `POST /station/orders/{id}/lines/{lineId}/cancel` and `POST /station/orders/{id}/void` with `{username, pin, reason}` plus an idempotency key, reusing the existing cancel/void logic (row lock, STORNO tickets). Only active admin or waiter accounts can confirm; wrong PINs count toward login brute-force protection. Record the confirming staff member and the station. Verify with tests: valid PIN cancels and prints STORNO, wrong PIN changes nothing, deactivated and station accounts are rejected, lockout after repeated wrong PINs, waiter still gets 403 on the admin endpoints
- [ ] 6.2 Return who cancelled and at which station in the order detail and the order history print status (waiting / unclear / failed). Verify with an `OrderController` test

## 7. App: station screen (spec: print-station; design D8)

- [ ] 7.1 Add `app/lib/station/` with `station_api.dart` and `station_screen.dart`, shown after login for the station role: printer status bar, ticket list polled every 2 s, unclear tickets pinned with "Was printed" and "Reprint", and reprint for other tickets. Verify with widget tests for the list, the pinned unclear ticket, and both resolutions
- [ ] 7.2 Add alerts: red banner with cause and a repeating sound for printer not ready, unclear ticket, oldest waiting ticket over 30 s, and no connection for over 10 s; one tap after login arms sound; wakelock keeps the screen on. Verify with widget tests for each alert cause and that the alert clears by itself
- [ ] 7.3 Add cancel line and void order from the station (order lookup from the ticket, reason, username and masked PIN, PIN cleared after each attempt). Verify with widget tests: success, wrong PIN message, PIN field empty afterwards
- [ ] 7.4 Admin: role `station` with printer assignment in `staff_screen.dart`, and printer status plus station last-seen in `printers_screen.dart`. Verify with widget tests

## 8. App: waiter-side changes (spec: order-taking)

- [ ] 8.1 Show the clear "may or may not have arrived, sending again is safe" message on connection failures in `order_screen.dart`, keeping the idempotency key until the draft changes. Verify with a widget test that a failed send shows the message and a resend uses the same key
- [ ] 8.2 Show waiting / unclear / failed print status with the reason, and "cancelled by X at station Y", in `order_history_screen.dart` and `order_detail_screen.dart`. Verify with widget tests

## 9. Event operation docs

- [ ] 9.1 Write `docs/event-setup.md`: network layout (router, switch, APs, wired printers and server recommended, separate 5 GHz staff SSID, fixed IPs), WiFi printer settings (DHCP reservation, power saving off, signal check), a UPS for router, switch, server, and printers, and station tablet setup (charger, kiosk/guided mode, sound armed). Verify by setting up one station tablet using only the guide

## 10. Load and failure tests (design D11)

- [ ] 10.1 Add a fake ePOS printer to the test sources that can answer slowly, drop the connection before or after reading the request, and report paper end or cover open. Verify with tests that each fault maps to `pending`, `unclear`, or the expected status
- [ ] 10.2 Add a load test with 15 simulated waiters at festival peak rate against two fake printers with injected faults. Verify that every order has exactly one ticket per printer that is `printed` or `unclear`, no ticket is delivered twice by the worker, and queues recover after each outage
- [ ] 10.3 Run backend tests, `flutter analyze`, and `flutter test`, and verify that everything passes
- [ ] 10.4 On site with real printers and station tablets: send orders from several waiters, pull paper, open the cover, unplug a printer's network, switch one printer to WiFi and drop it while printing, and restart the backend during printing. Verify that no ticket is lost or printed twice unnoticed, and record the results in `docs/acceptance.md`

## Workflow follow-up

- Optional after the hardware test: if the TM-m30III can report whether a print job ID was printed, resolve unclear tickets automatically (design D5); this only reduces manual resolutions and needs no spec change.
- Archive `add-pos-ordering-system` and `add-table-management` first, then this change, because its deltas modify requirements those changes add.
