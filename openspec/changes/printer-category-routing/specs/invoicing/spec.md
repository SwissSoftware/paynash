# Spec Delta

## MODIFIED Requirements

### Requirement: Receipt on the receipt printer
Issuing an invoice SHALL print it as a receipt on every active printer with receipts on. Staff and admins SHALL be able to print the receipt again at any time; a printed-again receipt SHALL be printed on the same printers as a new receipt and marked "KOPIE". The receipt of a cancelled invoice SHALL be marked "STORNIERT".

#### Scenario: Receipt after issuing
- **WHEN** an invoice is issued and a printer with receipts on is active
- **THEN** that printer prints the invoice with all its content and cuts the paper

#### Scenario: Copy for the customer
- **WHEN** a staff member prints the receipt of invoice "2026-0042" again
- **THEN** the same content is printed, marked "KOPIE"

#### Scenario: Copy with two receipt printers
- **WHEN** "Counter" and "Office" are active with receipts on and a staff member prints a copy
- **THEN** each of them prints the copy once

#### Scenario: No receipt printer
- **WHEN** an invoice is issued and no active printer has receipts on
- **THEN** the invoice is still issued, and the app warns that no receipt was printed
