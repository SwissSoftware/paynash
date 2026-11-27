# Spec Delta

## ADDED Requirements

### Requirement: Server errors keep the session
An unexpected server error during a request with a valid session SHALL be reported as a server error (HTTP 500) with a problem response, never as an authentication failure. The session SHALL stay valid, so the app stays logged in and the user can retry. The response SHALL NOT expose SQL, stack traces, or other internal details.

#### Scenario: Unexpected error with a valid session
- **WHEN** a logged-in waiter sends a request and the server fails with an unexpected error
- **THEN** the response has status 500 and a problem body with a generic detail, and the app does not return to the login screen

#### Scenario: Session still valid after the error
- **WHEN** a request with a valid session has just failed with a server error
- **THEN** the next request with the same session is accepted without a new login

#### Scenario: Unauthenticated request still rejected
- **WHEN** a request without a valid session reaches a non-login endpoint and the server would also fail on it
- **THEN** the request is rejected as unauthenticated (401) before any server error can occur

#### Scenario: No internal details
- **WHEN** a request fails with a database error
- **THEN** the problem body contains neither SQL text, table names, nor exception class names
