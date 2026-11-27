# Spec Delta

## MODIFIED Requirements

### Requirement: Issue an invoice
While `invoices` is on, staff and admins SHALL be able to issue an invoice for a confirmed order that has a customer, is not voided, and has at least one line that is not cancelled. An order SHALL have at most one invoice that is not cancelled. Issuing the same request twice SHALL create only one invoice.

#### Scenario: Issue after the treatment
- **WHEN** a staff member issues an invoice for a confirmed order of "Laura Meier" with 1 × "Facial" at 120.00
- **THEN** one invoice with total 120.00 is created, and the order shows it as invoiced

#### Scenario: Second invoice rejected
- **WHEN** staff try to issue an invoice for an order that already has an invoice that is not cancelled
- **THEN** the system rejects it and shows the existing invoice

#### Scenario: Voided order rejected
- **WHEN** staff try to issue an invoice for a voided order
- **THEN** the system rejects it

#### Scenario: Double tap
- **WHEN** the same issue request reaches the system twice after a network retry
- **THEN** only one invoice is created, and only one receipt is printed

#### Scenario: Order without customer
- **WHEN** staff try to issue an invoice for a table order that has no customer
- **THEN** the system rejects it with a message that the order needs a customer

### Requirement: Confirm and invoice in one step
While `invoices` is on, staff SHALL be able to confirm an order that has a customer and issue its invoice in one step. If the order is confirmed but the invoice cannot be issued, the order SHALL stay confirmed and the app SHALL show why the invoice is missing.

#### Scenario: One step at the counter
- **WHEN** a staff member builds an order for "Laura Meier" and taps "Confirm and invoice"
- **THEN** the order is confirmed, its invoice is issued, and the receipt is printed

#### Scenario: Business details missing
- **WHEN** a staff member taps "Confirm and invoice" and the business address is missing
- **THEN** the order is confirmed, no invoice is issued, and the app shows that the address is missing
