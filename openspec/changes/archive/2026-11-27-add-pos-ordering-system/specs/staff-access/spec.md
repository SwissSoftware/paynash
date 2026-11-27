# Spec Delta

## Purpose

Controls who can use the POS system. It covers staff accounts, the admin and waiter roles, login from the Android/iOS waiter app and the admin UI, and permission checks on every operation.

## ADDED Requirements

### Requirement: Staff accounts with roles
The system SHALL manage staff accounts. Each account has a unique username, a display name, exactly one role (`admin` or `waiter`), an active flag, and a hashed numeric PIN of 4 to 8 digits. Only admins SHALL create, edit, deactivate, or reset the PIN of staff accounts.

#### Scenario: Admin creates a waiter account
- **WHEN** an admin creates an account with username "anna", role `waiter`, and PIN "4711"
- **THEN** the account is stored with a hashed PIN and can be used to log in

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
Sessions SHALL expire after at most 12 hours. Staff SHALL be able to log out explicitly, which ends the session immediately.

#### Scenario: Explicit logout
- **WHEN** a waiter taps "Log out"
- **THEN** the session ends, and the next action requires a new login

#### Scenario: Expired session
- **WHEN** a request uses a session older than 12 hours
- **THEN** the system rejects it, and the app returns to the login screen

### Requirement: Role-based permissions
The system SHALL check every request against the caller's role. Waiters SHALL be allowed to view the active menu, send orders, view the order history, and trigger reprints. Admins SHALL be allowed to do everything waiters can, plus cancel lines of sent orders, void orders, and manage the menu, images, staff, printers, and exports.

#### Scenario: Waiter cannot export
- **WHEN** a waiter requests a CSV export
- **THEN** the system rejects the request as forbidden

#### Scenario: Unauthenticated request
- **WHEN** a request without a valid session reaches any non-login endpoint
- **THEN** the system rejects it as unauthenticated
