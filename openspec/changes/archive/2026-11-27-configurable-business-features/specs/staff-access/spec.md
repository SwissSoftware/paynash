# Spec Delta

## MODIFIED Requirements

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
