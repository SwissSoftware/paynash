# Design

## Context

See proposal.md for the two faults. Relevant current state:

- `SecurityConfig`: stateless bearer sessions, `TokenAuthenticationFilter extends OncePerRequestFilter` sets the authentication only in `SecurityContextHolder`. `authorizeHttpRequests` ends with `anyRequest().hasRole("WAITER")`. The entry point writes 401 via `Problems.write`.
- `OncePerRequestFilter` skips error dispatches by default (`shouldNotFilterErrorDispatch() == true`). Spring Security 6+ authorizes every dispatcher type, so the `ERROR` dispatch to `/error` arrives anonymous and is rejected with 401.
- `ApiExceptionHandler` (`ResponseEntityExceptionHandler`) handles `ApiException`, validation, and upload size. Everything else escapes to the container.
- Spring Boot 4.1, tests run as `@SpringBootTest` with MockMvc and Testcontainers (`IntegrationTest`).

## Goals / Non-Goals

**Goals:**
- An unexpected error never looks like an authentication failure.
- Unexpected errors are logged once with stack trace and answered with a uniform problem response.

**Non-Goals:**
- Re-authenticating error dispatches (running the token filter twice).
- Mapping specific database errors (e.g. constraint violations) to 4xx. That stays case by case via `ApiException`.

## Decisions

**D1 Permit the error dispatch instead of re-running the token filter.**
`authorizeHttpRequests` gets `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` as first rule. The original request was already authorized. The error dispatch only renders the response for it. Alternatives: overriding `shouldNotFilterErrorDispatch()` in the token filter would re-validate the token and touch the session again for every error. `requestMatchers("/error").permitAll()` would also open `/error` to direct requests, while the dispatcher-type rule applies only to real error dispatches.

**D2 Catch-all handler in `ApiExceptionHandler`.**
`@ExceptionHandler(Exception.class)` logs at ERROR level and returns `ProblemDetail` 500 with the detail "Unexpected server error". This keeps errors from controllers out of `/error` entirely, so D1 is only the safety net for errors thrown outside MVC (filters, view rendering). Spring's more specific handlers in `ResponseEntityExceptionHandler` and the existing `ApiException` handler keep precedence because Spring picks the closest matching exception type. `AccessDeniedException` and `AuthenticationException` must not be swallowed: the handler rethrows them so Spring Security still answers 403/401.

**D3 `ORDER_BY` as a plain string.**
`private static final String ORDER_BY = " ORDER BY lower(coalesce(last_name, '')), lower(coalesce(first_name, '')), number";`. A text block strips the common indentation, which for a single line includes the intended space. A plain string makes the space explicit. The other single-line text blocks in the backend were checked: none relies on a leading space.

**D4 Regression test for the 401 on a real port.**
MockMvc does not run the container's error dispatch, so a MockMvc test would pass even with the bug. The new test uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a test-only controller (in `src/test`) that throws an unchecked exception, once from the controller and once from a servlet filter (to exercise D1 without D2). It asserts 500, a problem body without internals, and that the same token still works afterwards.

**D5 Problem responses from `/error` as well.**
Found during implementation: with D1 alone, errors outside MVC reach Spring Boot's `BasicErrorController`, which answers 500 with its own JSON (`timestamp`, `status`, `error`, `path`) instead of an RFC 9457 problem. A small `ProblemErrorController` in `common` implements Boot's `ErrorController`, which replaces `BasicErrorController`. On an error dispatch it returns a `ProblemDetail` with the original status (`jakarta.servlet.error.status_code`), with the detail "Unexpected server error" for 5xx and the reason phrase otherwise. It does not log, because the container already logs the exception. Requests to `/error` that are not error dispatches get 404. Alternative: custom `ErrorAttributes` for `BasicErrorController` would change the body fields but not the content type, and the app would still see two error formats.

## Risks / Trade-offs

- [The catch-all handler hides exceptions that Spring would otherwise map, e.g. `ResponseStatusException`] → Handle `ErrorResponse` instances by returning their own status and body, and cover this with a test case.
- [Permitting the error dispatch could leak an error page to unauthenticated callers] → Unauthenticated requests are rejected by the authorization filter before any controller runs, so they never produce an error dispatch with internals. The scenario "Unauthenticated request still rejected" covers this.
- [Logs grow if an error repeats] → Accepted. Repeated errors must be visible.
