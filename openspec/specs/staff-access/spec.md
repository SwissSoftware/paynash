# staff-access Specification

## Purpose

Controls who can use the POS system. It covers staff accounts, the admin and waiter roles, login from the Android/iOS waiter app and the admin UI, and permission checks on every operation.

## Requirements

### Requirement: Staff accounts with roles
The system SHALL manage staff accounts. Each account has a unique username, a display name, exactly one role (`admin`, `waiter`, or `station`), an active flag, and a hashed numeric PIN of 4 to 8 digits. A `station` account SHALL also have one or more assigned printers. Only admins SHALL create, edit, deactivate, or reset the PIN of staff accounts.

#### Scenario: Admin creates a waiter account
- **WHEN** an admin creates an account with username "anna", role `waiter`, and PIN "4711"
- **THEN** the account is stored with a hashed PIN and can be used to log in

#### Scenario: Admin creates a station account
- **WHEN** an admin creates an account "station-bar" with role `station` and assigns the printer "Bar"
- **THEN** the account can log in and sees the station view for "Bar"

#### Scenario: Station without printer rejected
- **WHEN** an admin creates a `station` account without an assigned printer
- **THEN** the system rejects the request with a validation error

#### Scenario: Duplicate username rejected
- **WHEN** an admin creates an account with a username that already exists
- **THEN** the system rejects the request with a validation error and creates no account

#### Scenario: Waiter cannot manage accounts
- **WHEN** a logged-in waiter tries to create or edit a staff account
- **THEN** the system rejects the request as forbidden

### Requirement: Login from Android and iOS
The waiter app SHALL run on Android and iOS and SHALL let an active staff member log in with username and PIN. After a successful login, the app SHALL keep the session so the waiter does not have to log in again for each order.

#### Scenario: Successful waiter login on a phone
- **WHEN** an active waiter enters a correct username and PIN in the app on Android or iOS
- **THEN** the app opens the order screen and shows the waiter's name

#### Scenario: Wrong PIN
- **WHEN** a user enters a wrong PIN
- **THEN** login fails with a generic "invalid credentials" message that does not reveal whether the username exists

#### Scenario: Deactivated account
- **WHEN** a deactivated staff member tries to log in
- **THEN** login fails, and any existing sessions of that account are rejected on their next request

### Requirement: Brute-force protection
The system SHALL temporarily lock an account after 5 consecutive failed login attempts and keep it locked for at least 5 minutes.

#### Scenario: Lockout after repeated failures
- **WHEN** 5 consecutive login attempts for one account fail
- **THEN** further attempts for that account are rejected for at least 5 minutes, even with the correct PIN

### Requirement: Session expiry and logout
Sessions of admins and waiters SHALL expire after at most 12 hours. Sessions of stations SHALL expire after at most 24 hours. Staff SHALL be able to log out explicitly, which ends the session immediately.

#### Scenario: Explicit logout
- **WHEN** a waiter taps "Log out"
- **THEN** the session ends, and the next action requires a new login

#### Scenario: Expired session
- **WHEN** a request uses a waiter session older than 12 hours
- **THEN** the system rejects it, and the app returns to the login screen

#### Scenario: Station through a long event day
- **WHEN** a station logged in at 10:00 is still in use at 23:00
- **THEN** its session is still valid

### Requirement: Role-based permissions
The system SHALL check every request against the caller's role and the enabled features. Waiters SHALL view the active menu, send orders, view the order history, and trigger reprints; with `customers` also manage customers; with `invoices` also issue invoices, print receipts, download invoice PDFs, and mark invoices paid. Admins SHALL do everything waiters can, plus cancel lines, void orders, manage the menu, images, staff, printers, exports, and settings; with `customers` also delete customers; with `invoices` also cancel invoices and undo payments; with `sales-report` also use the report. Stations SHALL view the ticket list and printer status of their assigned printers, reprint and resolve their tickets, and cancel lines and void orders with a staff PIN, and nothing else.

#### Scenario: Waiter cannot export
- **WHEN** a waiter requests a CSV export
- **THEN** the system rejects the request as forbidden

#### Scenario: Station cannot send orders
- **WHEN** a station tries to send an order or open the menu, staff, printer, or export administration
- **THEN** the system rejects the request as forbidden

#### Scenario: Station limited to its printers
- **WHEN** the bar station requests the ticket list of the kitchen printer
- **THEN** the system rejects the request as forbidden

#### Scenario: Unauthenticated request
- **WHEN** a request without a valid session reaches any non-login endpoint
- **THEN** the system rejects it as unauthenticated

#### Scenario: Salon staff issue and collect
- **WHEN** `customers` and `invoices` are on and a waiter creates a customer, issues an invoice, and marks it paid by cash
- **THEN** all three requests succeed

#### Scenario: Salon staff cannot see the report
- **WHEN** `sales-report` is on and a waiter requests the sales report
- **THEN** the system rejects the request as forbidden

#### Scenario: Station in a salon
- **WHEN** someone logs in with a `station` account in an installation where `stations` is off, such as a salon
- **THEN** the system rejects the login, because the installation has no station view

#### Scenario: Station sessions after switching stations off
- **WHEN** an admin switches off `stations` while a station is logged in
- **THEN** the station's next request is rejected, and the station returns to the login screen

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
