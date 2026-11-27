# Spec Delta

## ADDED Requirements

### Requirement: Small-amount limit without customer
An invoice for an order without a customer SHALL have a total of at most CHF 400.00, the limit Swiss VAT law sets for receipts without the recipient's name and address. A higher total SHALL be rejected with a message that a customer is needed. Orders with a customer SHALL have no such limit.

#### Scenario: Exactly at the limit
- **WHEN** staff issue an invoice for an order without customer with the total 400.00
- **THEN** the invoice is issued without recipient

#### Scenario: Above the limit
- **WHEN** staff issue an invoice for an order without customer with the total 400.05
- **THEN** the system rejects it with "Invoices above CHF 400.00 need a customer", and no invoice number is used

#### Scenario: Large invoice with customer
- **WHEN** staff issue an invoice for an order of "Muster AG" with the total 1250.00
- **THEN** the invoice is issued with "Muster AG" as recipient

## MODIFIED Requirements

### Requirement: Issue an invoice
While `invoices` is on, staff and admins SHALL be able to issue an invoice for a confirmed order that is not voided and has at least one line that is not cancelled, with or without a customer. An order SHALL have at most one invoice that is not cancelled. Issuing the same request twice SHALL create only one invoice.

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
- **WHEN** staff issue an invoice for a table order without customer with 2 × "Espresso" at 4.50
- **THEN** one invoice with total 9.00 and without recipient is created, and the order shows it as invoiced

### Requirement: Confirm and invoice in one step
While `invoices` is on, staff SHALL be able to confirm an order, with or without a customer, and issue its invoice in one step. If the order is confirmed but the invoice cannot be issued, the order SHALL stay confirmed and the app SHALL show why the invoice is missing.

#### Scenario: One step at the counter
- **WHEN** a staff member builds an order for "Laura Meier" and taps "Confirm and invoice"
- **THEN** the order is confirmed, its invoice is issued, and the receipt is printed

#### Scenario: Business details missing
- **WHEN** a staff member taps "Confirm and invoice" and the business address is missing
- **THEN** the order is confirmed, no invoice is issued, and the app shows that the address is missing

#### Scenario: Counter sale with receipt
- **WHEN** at a café with `invoices` on and `customers` off a staff member sells 1 × "Croissant" and taps "Sell and invoice"
- **THEN** the order is saved, an invoice without recipient is issued, and the receipt is printed

#### Scenario: Limit reached in one step
- **WHEN** a staff member taps "Send and invoice" for an order without customer with the total 520.00
- **THEN** the order is sent, no invoice is issued, and the app shows that invoices above CHF 400.00 need a customer

### Requirement: Invoice content
An invoice SHALL show the business details, the invoice number, the invoice date, each line with quantity, service name, unit price, and amount, and the total in CHF. If the invoice has a customer, it SHALL also show the customer number, the customer name and (if known) address; without customer, it SHALL show no recipient. Cancelled lines SHALL NOT appear. Invoices SHALL be in German.

#### Scenario: Two services
- **WHEN** an invoice is issued for 1 × "Facial" at 120.00 and 2 × "Eyebrow tint" at 25.00
- **THEN** it shows both lines with the amounts 120.00 and 50.00 and the total 170.00

#### Scenario: Receipt without recipient
- **WHEN** an invoice without customer is printed as receipt and downloaded as PDF
- **THEN** both show the business details, number, date, lines, and total, and neither shows a customer number, name, or address
