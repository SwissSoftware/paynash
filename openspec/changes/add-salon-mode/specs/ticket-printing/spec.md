# Spec Delta

## MODIFIED Requirements

### Requirement: Printer configuration
Admins SHALL configure printers. Each printer has a name, a network address (IP or hostname), the ePOS device ID, an active flag, and what it serves: an item type (`food` or `drink`) or `receipt`. Exactly one active printer SHALL be assigned to each item type in event mode, and to `receipt` in salon mode. Admins SHALL be able to send a test print.

#### Scenario: Configure bar printer
- **WHEN** an admin adds the printer "Bar" at 192.168.1.50, assigned to `drink`
- **THEN** future drink lines are routed to "Bar"

#### Scenario: Test print
- **WHEN** an admin triggers a test print for "Kitchen"
- **THEN** the printer prints a test ticket, and the admin sees whether it succeeded or failed

#### Scenario: Missing routing
- **WHEN** no active printer is assigned to `food` and an order with food lines is sent
- **THEN** the order is still saved, the food ticket is marked failed with "no printer configured", and the waiter is warned

#### Scenario: Configure a salon receipt printer
- **WHEN** an admin in salon mode adds the printer "Counter" at 192.168.1.60, assigned to `receipt`
- **THEN** future invoice receipts are printed on "Counter"

#### Scenario: Second active receipt printer rejected
- **WHEN** an admin activates a second printer assigned to `receipt`
- **THEN** the system rejects it with a validation error

## ADDED Requirements

### Requirement: No production tickets for services
Lines of type `service` SHALL NOT be routed to any printer. Confirming an order, cancelling its lines, or voiding it in salon mode SHALL NOT create production or cancellation tickets.

#### Scenario: Confirm a service order
- **WHEN** an order with 1 × "Facial" is confirmed
- **THEN** no ticket is created for any printer

#### Scenario: Void a service order
- **WHEN** an admin voids an order with 1 × "Facial" that has no invoice
- **THEN** the order becomes `voided`, and no STORNO ticket is printed

### Requirement: Receipt print jobs
Invoice receipts SHALL be print jobs on the active receipt printer with the same statuses and guarantees as production tickets: they wait for the printer instead of failing, are not printed twice automatically, and become `unclear` if the system restarts while printing. If no receipt printer is active, the receipt job SHALL be `failed` with "no printer configured".

#### Scenario: Receipt printer out of paper
- **WHEN** an invoice is issued while the receipt printer has no paper, and the roll is replaced 3 minutes later
- **THEN** the receipt prints by itself once the paper is in

#### Scenario: Unclear receipt
- **WHEN** the receipt printer accepted a receipt but did not answer
- **THEN** the receipt is `unclear`, is not printed again automatically, and the invoice shows "check the receipt and print a copy if needed"

### Requirement: Receipt status on the invoice
In salon mode, the invoice view SHALL show the status of its latest receipt job and the printer's error if it is waiting, unclear, or failed. Staff and admins SHALL be able to print a copy from there.

#### Scenario: Receipt failed
- **WHEN** the receipt of invoice "2026-0042" failed with "no printer configured"
- **THEN** the invoice view shows the error, and after an admin configured a receipt printer, staff can print a copy

### Requirement: Receipt content
A receipt SHALL contain the full invoice content in a layout for 80 mm paper: business details, invoice number and date, customer, lines with quantity, name, and amount, total, and the VAT breakdown and UID if the invoice has VAT. After printing, the paper SHALL be cut.

#### Scenario: Receipt with VAT
- **WHEN** the receipt of an invoice with VAT is printed
- **THEN** it shows the UID and the gross, net, and VAT amounts per rate below the total
