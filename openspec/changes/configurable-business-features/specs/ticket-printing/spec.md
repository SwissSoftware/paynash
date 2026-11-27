# Spec Delta

## MODIFIED Requirements

### Requirement: Printer configuration
Admins SHALL configure printers. Each printer has a name, a network address (IP or hostname), the ePOS device ID, an active flag, a "tickets" switch with the menu categories whose lines it prints, and a "receipts" switch for invoice receipts. A category MAY be assigned to several printers, and several printers MAY have receipts on. The "tickets" switch SHALL be available with any features. The "receipts" switch SHALL be shown only while `invoices` is on. Admins SHALL be able to send a test print.

#### Scenario: Configure bar printer
- **WHEN** an admin adds the printer "Bar" at 192.168.1.50 with tickets on and the categories "Beer" and "Soft drinks"
- **THEN** future lines of "Beer" and "Soft drinks" are routed to "Bar"

#### Scenario: Test print
- **WHEN** an admin triggers a test print for "Kitchen"
- **THEN** the printer prints a test ticket, and the admin sees whether it succeeded or failed

#### Scenario: Missing routing
- **WHEN** the category "Pizza" is assigned only to inactive printers and an order with food lines of "Pizza" is sent
- **THEN** the order is still saved, the tickets for those printers are marked failed with "no active printer", and the waiter is warned

#### Scenario: Category on two printers
- **WHEN** an admin assigns the category "Pizza" to both "Kitchen" and "Pass"
- **THEN** the system accepts it, and both printers list "Pizza"

#### Scenario: Configure a receipt printer
- **WHEN** an admin with `invoices` on adds the printer "Counter" at 192.168.1.60 with receipts on and tickets off
- **THEN** the printer is saved, and future invoice receipts are printed on "Counter"

#### Scenario: Second active receipt printer accepted
- **WHEN** an admin activates a second printer with receipts on
- **THEN** the system accepts it, and both printers print future receipts

#### Scenario: Ticket printer without tables
- **WHEN** an admin with `tables` off adds a printer with tickets on and the category "Hair"
- **THEN** the printer is saved, and lines of "Hair" print tickets on it

#### Scenario: Deleted category
- **WHEN** an admin deletes the category "Specials", which was assigned to "Kitchen"
- **THEN** "Kitchen" no longer lists "Specials", and the printer is otherwise unchanged

### Requirement: Receipt status on the invoice
While `invoices` is on, the invoice view SHALL show, for each printer that received the invoice's receipt, the status of its latest receipt job and the printer's error if it is waiting, unclear, or failed. Staff and admins SHALL be able to print a copy from there.

#### Scenario: Receipt failed
- **WHEN** the receipt of invoice "2026-0042" failed with "no active printer"
- **THEN** the invoice view shows the error, and after an admin activated a receipt printer, staff can print a copy

#### Scenario: One of two receipts unclear
- **WHEN** the receipt of an invoice printed on "Counter" but is `unclear` on "Office"
- **THEN** the invoice view shows "Counter" as printed and "Office" as unclear
