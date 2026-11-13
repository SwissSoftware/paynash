# Spec Delta

## Purpose

Lets a salon give its customers an invoice for the services they received: as a receipt on the receipt printer and as an A4 PDF. It also tracks whether each invoice is paid, and how.

## ADDED Requirements

### Requirement: Issue an invoice
In salon mode, staff and admins SHALL be able to issue an invoice for a confirmed order that has a customer, is not voided, and has at least one line that is not cancelled. An order SHALL have at most one invoice that is not cancelled. Issuing the same request twice SHALL create only one invoice.

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

### Requirement: Confirm and invoice in one step
In salon mode, staff SHALL be able to confirm an order and issue its invoice in one step. If the order is confirmed but the invoice cannot be issued, the order SHALL stay confirmed and the app SHALL show why the invoice is missing.

#### Scenario: One step at the counter
- **WHEN** a staff member builds an order for "Laura Meier" and taps "Confirm and invoice"
- **THEN** the order is confirmed, its invoice is issued, and the receipt is printed

#### Scenario: Business details missing
- **WHEN** a staff member taps "Confirm and invoice" and the business address is missing
- **THEN** the order is confirmed, no invoice is issued, and the app shows that the address is missing

### Requirement: Invoice numbers
Each invoice SHALL get a number in the format `YYYY-NNNN`, where `YYYY` is the year of the invoice date and `NNNN` counts up from 0001 within that year. Numbers SHALL be unique and without gaps. A number SHALL never be reused, also not after the invoice is cancelled.

#### Scenario: First invoice of a year
- **WHEN** the first invoice of 2027 is issued on 2 January 2027
- **THEN** it gets the number "2027-0001"

#### Scenario: No gaps under load
- **WHEN** two devices issue invoices at the same moment and the last number was "2026-0041"
- **THEN** the invoices get "2026-0042" and "2026-0043"

### Requirement: Invoice content
An invoice SHALL show the business details, the invoice number, the invoice date, the customer number, the customer name and (if known) address, each line with quantity, service name, unit price, and amount, and the total in CHF. Cancelled lines SHALL NOT appear. Invoices SHALL be in German.

#### Scenario: Two services
- **WHEN** an invoice is issued for 1 × "Facial" at 120.00 and 2 × "Eyebrow tint" at 25.00
- **THEN** it shows both lines with the amounts 120.00 and 50.00 and the total 170.00

### Requirement: VAT on invoices
When VAT is turned on at the time of issuing, the invoice SHALL show the UID and, per VAT rate, the rate, the net amount, the VAT amount, and the gross amount. Prices SHALL include VAT. The VAT amount per rate SHALL be the gross amount × rate / (100 + rate), rounded to 0.01 CHF.

#### Scenario: Standard rate
- **WHEN** VAT is on and an invoice of 108.10 contains only services at the rate 8.1 %
- **THEN** it shows gross 108.10, VAT 8.10, and net 100.00 for the rate 8.1 %, and the UID

#### Scenario: VAT off
- **WHEN** VAT is off at the time of issuing
- **THEN** the invoice shows no VAT rate, no VAT amount, and no UID

### Requirement: Invoices are final
An issued invoice SHALL NOT change. It SHALL keep the business details, customer details, lines, prices, and VAT rates that were valid when it was issued. While an invoice is not cancelled, its order's lines SHALL NOT be cancelled and the order SHALL NOT be voided.

#### Scenario: Customer moves
- **WHEN** a customer's address changes after an invoice was issued
- **THEN** the invoice still shows the old address, and new invoices show the new one

#### Scenario: Cancel a line of an invoiced order
- **WHEN** an admin tries to cancel a line of an order whose invoice is not cancelled
- **THEN** the system rejects it with "cancel the invoice first"

### Requirement: Cancel an invoice
Only admins SHALL be able to cancel an invoice, with a required reason. A cancelled invoice SHALL keep its number and content, SHALL be marked "STORNIERT" with the reason and date, and SHALL NOT count as revenue. A paid invoice SHALL first be set back to open. After cancelling, the order can be corrected and invoiced again with a new number.

#### Scenario: Wrong service invoiced
- **WHEN** an admin cancels invoice "2026-0042" with the reason "wrong service", cancels the wrong line, and issues a new invoice
- **THEN** "2026-0042" is shown as cancelled, and the new invoice "2026-0043" contains only the correct lines

#### Scenario: Paid invoice cannot be cancelled
- **WHEN** an admin tries to cancel a paid invoice
- **THEN** the system rejects it with "set the invoice back to open first"

#### Scenario: Staff cannot cancel
- **WHEN** a staff member tries to cancel an invoice
- **THEN** the system rejects the request as forbidden

### Requirement: Receipt on the receipt printer
Issuing an invoice SHALL print it as a receipt on the active receipt printer. Staff and admins SHALL be able to print the receipt again at any time. A printed-again receipt SHALL be marked "KOPIE". The receipt of a cancelled invoice SHALL be marked "STORNIERT".

#### Scenario: Receipt after issuing
- **WHEN** an invoice is issued and a receipt printer is active
- **THEN** the receipt printer prints the invoice with all its content and cuts the paper

#### Scenario: Copy for the customer
- **WHEN** a staff member prints the receipt of invoice "2026-0042" again
- **THEN** the same content is printed, marked "KOPIE"

#### Scenario: No receipt printer
- **WHEN** an invoice is issued and no receipt printer is active
- **THEN** the invoice is still issued, and the app warns that no receipt was printed

### Requirement: A4 invoice as PDF
Staff and admins SHALL be able to download any invoice as an A4 PDF with the same content as the receipt, laid out for printing or sending to the customer. Downloading SHALL NOT change the invoice. The PDF of a cancelled invoice SHALL be marked "STORNIERT".

#### Scenario: Download PDF
- **WHEN** a staff member downloads the PDF of invoice "2026-0042"
- **THEN** a PDF named "Rechnung-2026-0042.pdf" in A4 format with the invoice content is downloaded

#### Scenario: Same PDF later
- **WHEN** the PDF of an invoice is downloaded again a month later after the business address changed
- **THEN** it shows the business address that was valid when the invoice was issued

### Requirement: Payment status
Each invoice SHALL be `open` when issued. Staff and admins SHALL be able to mark an open invoice as `paid` with a payment method (`cash`, `card`, `twint`, or `bank_transfer`) and a payment date that is not before the invoice date and not in the future. The system SHALL record who marked it paid.

#### Scenario: Paid by TWINT at the counter
- **WHEN** a staff member marks invoice "2026-0042" as paid by `twint` today
- **THEN** the invoice is `paid` with method TWINT, today's date, and the staff member's name

#### Scenario: Payment date in the future rejected
- **WHEN** a staff member enters a payment date in the future
- **THEN** the system rejects it with a validation error

#### Scenario: Cancelled invoice cannot be paid
- **WHEN** staff try to mark a cancelled invoice as paid
- **THEN** the system rejects it

### Requirement: Undo a payment
Only admins SHALL be able to set a paid invoice back to `open`, with a required reason. The system SHALL keep a record of the removed payment, who removed it, and why.

#### Scenario: Payment marked by mistake
- **WHEN** an admin sets invoice "2026-0042" back to open with the reason "wrong invoice marked"
- **THEN** the invoice is `open` again, and its history shows the removed payment and the reason

### Requirement: Invoice list
Staff and admins SHALL see a list of invoices for a chosen period (default the current month), newest first, with number, date, customer, total, and status (`open`, `paid`, or cancelled). The list SHALL be filterable by status and searchable by invoice number and customer name.

#### Scenario: Open invoices
- **WHEN** an admin filters the invoice list of the current month by `open`
- **THEN** only invoices that are open are listed, with their totals
