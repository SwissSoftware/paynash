# Spec Delta

## MODIFIED Requirements

### Requirement: Role-based permissions
The system SHALL check every request against the caller's role and the installation mode. Waiters SHALL be allowed to view the active menu, send orders, view the order history, and trigger reprints, and in salon mode also manage customers, issue invoices, print receipts, download invoice PDFs, and mark invoices paid. Admins SHALL be allowed to do everything waiters can, plus cancel lines, void orders, and manage the menu, images, staff, printers, and exports, and in salon mode also delete customers, cancel invoices, undo payments, change settings, and use the sales report. Stations SHALL be allowed to view the station ticket list and printer status of their assigned printers, reprint and resolve those printers' tickets, and cancel lines and void orders with a staff PIN confirmation, and nothing else.

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
- **WHEN** a waiter in salon mode creates a customer, issues an invoice, and marks it paid by cash
- **THEN** all three requests succeed

#### Scenario: Salon staff cannot see the report
- **WHEN** a waiter in salon mode requests the sales report
- **THEN** the system rejects the request as forbidden

#### Scenario: Station in a salon
- **WHEN** someone logs in with a `station` account in salon mode
- **THEN** the system rejects the login, because salon mode has no station view
