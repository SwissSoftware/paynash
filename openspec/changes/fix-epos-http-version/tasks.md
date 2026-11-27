# Tasks

## 1. Reproduce in tests

- [x] 1.1 Extend `FakeEposPrinter` (design D2): record the request headers, and close the exchange without a response when a request carries an `Upgrade` header. Run the printing tests (`./mvnw test -Dtest='ch.paynash.pos.printing.*Test,OrderPrintStatusTest,StationResolveTest'` with JDK 21) and verify that the tests that print through `EposClient` now fail with "no answer from printer".
- [x] 1.2 Add an `EposClientTest` case that prints a ticket to `FakeEposPrinter` and asserts the result is `Printed` and the recorded request has no `Upgrade` header. Verify it fails before the fix.

## 2. Fix

- [x] 2.1 Pin `HttpClient.Version.HTTP_1_1` in the `EposClient` builder with a short comment why (design D1). Verify the tests from 1.1 and 1.2 pass.

## 3. Integration check

- [ ] 3.1 Run the full backend suite (`./mvnw verify` in `backend/` with JDK 21) and verify it is green. Then restart the local backend, trigger a test print for the printer at 192.168.1.115 in the admin UI and print a receipt, and verify both come out of the printer.
