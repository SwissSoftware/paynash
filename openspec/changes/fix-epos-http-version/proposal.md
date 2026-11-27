# Proposal

## Why

No ticket, receipt, or test print reaches a real Epson printer. `EposClient` builds its `java.net.http.HttpClient` without setting a version, so Java uses its default `HTTP_2`. For `http://` addresses this sends the request as HTTP/1.1 with `Connection: Upgrade, HTTP2-Settings` and `Upgrade: h2c`. The printer's embedded web server (`EPSON-HTTP/1.0`) does not support this and closes the connection without an answer. Java reports `IOException: HTTP/1.1 header parser received no bytes`, which `EposClient` maps to "no answer from printer; check the ticket and reprint if needed".

Verified against a TM printer at 192.168.1.115 with an empty ePOS document (prints nothing): the default client fails after 94 ms, the same request with `HTTP_1_1` returns `success="true"` in 368 ms. curl and the commercial POS on the same network print fine. The tests did not catch it because `FakeEposPrinter` (JDK `HttpServer`) ignores the upgrade request.

## What Changes

- `EposClient` sends every ePOS request as plain HTTP/1.1 without an upgrade attempt.
- `FakeEposPrinter` behaves like the real printer: a request that asks for a protocol upgrade gets no answer. All existing printing tests then guard against the regression.
- A focused `EposClientTest` case asserts that a request reaches the printer without `Upgrade` header.

Not in scope:
- Automatic printer discovery on the network (possible later change `add-printer-discovery`).
- A more helpful text for `DeviceNotFound`. The message "device ID not found on the printer" is specified word for word in `harden-printing-for-events`, so changing it belongs there.
- The idle reachability check (`reachable()`, TCP connect only). It reports the right thing once printing works.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `ticket-printing`: New requirement that the system talks to printers in a way their embedded web server accepts (plain HTTP/1.1, no protocol upgrade).

## Impact

- Backend: `printing/EposClient.java` (one builder setting).
- Tests: `support/FakeEposPrinter.java`, `printing/EposClientTest.java`.
- Affects every print path: production tickets, STORNO, reprints, receipts, and test prints. No API, database, or app change.
