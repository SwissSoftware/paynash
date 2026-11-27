# Tasks

## 1. Customer list query

- [x] 1.1 In `CustomerControllerTest`, add tests for `GET /customers` without `q` (waiter: only active customers, sorted by last and first name) and `GET /customers?includeInactive=true` without `q` (admin: active and inactive). Verify both fail with 500 before the fix.
- [x] 1.2 Change `ORDER_BY` in `CustomerRepository` to a plain string with a leading space (design D3). Verify the tests from 1.1 and all existing `CustomerControllerTest` tests pass.

## 2. Server errors as 500 instead of 401

- [x] 2.1 Add `ServerErrorSessionTest` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and a test-only controller and filter that throw an unchecked exception (design D4). Assert for both paths: status 500, problem body without SQL, table, or exception class names, and that a follow-up request with the same token returns 200. Add a case without token that expects 401. Verify the 500 cases fail with 401 before the fix.
- [x] 2.2 In `SecurityConfig`, permit `DispatcherType.ERROR` as the first authorization rule (design D1). Verify the filter path of 2.1 returns 500 instead of 401 with the session kept, and `PermissionTest` and `AuthControllerTest` still pass.
- [x] 2.3 In `ApiExceptionHandler`, add the catch-all handler from design D2: log at ERROR, return a 500 problem with detail "Unexpected server error", return status and body of `ErrorResponse` exceptions unchanged, rethrow `AccessDeniedException` and `AuthenticationException`. Verify the controller path of 2.1 passes, plus a test that a `ResponseStatusException(404)` still yields 404.
- [x] 2.4 Add `ProblemErrorController` (design D5). Verify the filter path of 2.1 passes completely (problem content type and generic detail), and add a test that a direct `GET /error` with a valid token returns 404.

## 3. Integration check

- [x] 3.1 Run the full backend test suite (`./mvnw verify` in `backend/`) and verify it is green. Then start the backend locally in salon mode, log in with the app, open the customer list without a search term, and verify that the list loads and no logout happens.
