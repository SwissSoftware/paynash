# Proposal

## Why

In salon mode, a logged-in user is thrown back to the login screen as soon as the customer list opens. Two faults combine:

1. The unfiltered customer list always fails with a SQL syntax error. In `CustomerRepository`, the single-line text block `ORDER_BY` loses its leading space, so the query becomes `... WHERE activeORDER BY ...` (or `... FROM customerORDER BY ...` with inactive customers included). Searches with a term work by chance because the condition ends with `)`. The tests only search with `q`, so they never caught it.
2. Any unhandled exception in the backend is answered with **401 instead of 500**. Spring forwards it to `/error`. On that error dispatch the bearer token filter (`OncePerRequestFilter`) does not run, the stateless setup keeps no authentication, and `/error` falls under `anyRequest().hasRole("WAITER")`. The entry point then writes 401. The app treats every 401 with a token as an expired session (`api_client.dart`) and logs the user out.

The second fault affects both modes: any unexpected server error ends the session of the waiter or salon staff. In the middle of service that is unacceptable.

## What Changes

- Fix the customer list query so that listing without a search term works, with and without inactive customers.
- Return server errors as **500 problem responses** to authenticated callers instead of 401. The error dispatch must no longer be blocked by the authorization rules.
- Add a catch-all exception handler that logs unexpected exceptions and answers with a generic RFC 9457 problem (500) without exposing SQL or stack traces.
- Add regression tests: the customer list without `q`, and an unexpected server error with a valid token that yields 500 and keeps the session valid.

Not in scope:
- Changes to the app's 401 handling. The app behaves as specified once the backend sends the right status.
- Retry or error UI changes in the app.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `staff-access`: New requirement that server errors do not end or invalidate the session and are reported as server errors, not as authentication failures.

The customer list fix needs no spec change. `customer-management` (still in the change `add-salon-mode`) already requires that results list active customers. The query simply does not do this today.

## Impact

- Backend: `customer/CustomerRepository.java`, `auth/SecurityConfig.java`, `common/ApiExceptionHandler.java`, new `common/ProblemErrorController.java` (replaces Spring Boot's default error controller).
- Tests: `customer/CustomerControllerTest.java`, a new test running on a real HTTP port (MockMvc does not perform the servlet container's error dispatch, so it cannot reproduce the 401).
- API: Unexpected errors change from 401 to 500 with a problem body. Clients that relied on 401 for these cases do not exist. The app already shows the `detail` of error responses.
- No database migration, no app change.
