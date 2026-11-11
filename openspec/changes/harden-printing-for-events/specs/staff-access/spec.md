# Spec Delta

## MODIFIED Requirements

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
The system SHALL check every request against the caller's role. Waiters SHALL be allowed to view the active menu, send orders, view the order history, and trigger reprints. Admins SHALL be allowed to do everything waiters can, plus cancel lines of sent orders, void orders, and manage the menu, images, staff, printers, and exports. Stations SHALL be allowed to view the station ticket list and printer status of their assigned printers, reprint and resolve those printers' tickets, and cancel lines and void orders with a staff PIN confirmation, and nothing else.

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
