# Design

## Context

See proposal.md for the fault and how it was verified. `EposClient` (design D3 of `add-pos-ordering-system`) is the only place that talks HTTP to printers: `print()` for tickets, receipts, and test prints, and `reachable()` (plain TCP connect) for the status monitor. `FakeEposPrinter` is a JDK `com.sun.net.httpserver.HttpServer` that most printing tests share. It accepts any HTTP/1.1 request and ignores `Upgrade` headers, which is why the bug went unnoticed.

## Goals / Non-Goals

**Goals:**
- Every request from `EposClient` is accepted by Epson's embedded web server.
- The test double rejects what the real printer rejects, so a regression fails many tests at once.

**Non-Goals:**
- Supporting HTTPS printers differently. With `https://` addresses Java negotiates via ALPN, and HTTP/1.1 is pinned there as well, which Epson's HTTPS server supports.
- Changing timeouts or the result mapping.

## Decisions

**D1 Pin `HttpClient.Version.HTTP_1_1` in the `EposClient` builder.**
Printers never speak HTTP/2, so there is nothing to gain from the default. Alternatives: removing upgrade headers per request is not possible with `java.net.http` (restricted headers). Switching to another HTTP library would be a larger change for the same effect.

**D2 `FakeEposPrinter` emulates the printer's reaction to upgrade requests.**
If a request carries an `Upgrade` header, the fake closes the exchange without sending a response, as `EPSON-HTTP/1.0` does. This is the default for every test, not an opt-in fault, so every printing test (worker, reprint, receipts, test print) covers the regression. The fake also records the request headers, so `EposClientTest` can assert the absence of `Upgrade` directly.

## Risks / Trade-offs

- [Other printer firmwares might behave differently] → HTTP/1.1 is the protocol all Epson ePOS-Print documentation uses, so pinning it is the safe choice for every model.
- [Emulating the close in the fake could hide other connection-handling bugs] → The fake only closes for requests with an `Upgrade` header. All other behaviour stays as before.
