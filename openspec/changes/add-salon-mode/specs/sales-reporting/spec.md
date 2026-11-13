# Spec Delta

## Purpose

Gives the salon owner an overview of revenue for a period, per day, per service, per customer, and per payment method, on the devices and as an Excel file for bookkeeping.

## ADDED Requirements

### Requirement: Revenue basis
Revenue SHALL be the total of the invoices that are not cancelled, counted on the invoice date in the salon's local time. Cancelled invoices, cancelled lines, voided orders, and orders without invoice SHALL NOT count. Amounts SHALL be in CHF with two decimals, without rounding errors.

#### Scenario: Cancelled invoice excluded
- **WHEN** a month has invoices of 120.00 and 80.00, and the 80.00 invoice is cancelled
- **THEN** the revenue of the month is 120.00

#### Scenario: Invoice issued after the order
- **WHEN** an order was confirmed on 30 November but its invoice was issued on 1 December
- **THEN** its amount counts toward 1 December

### Requirement: Report period
The report SHALL cover a chosen calendar month (default the current month) or a date range of at most 366 days. The end date SHALL NOT be before the start date.

#### Scenario: Month report
- **WHEN** an admin opens the report and picks October 2026
- **THEN** the report covers 1 to 31 October 2026

#### Scenario: Range too long
- **WHEN** an admin picks a range of 400 days
- **THEN** the system rejects it with a validation error

### Requirement: Report summary
The report SHALL show, for the period, the total revenue, the number of invoices, the paid amount, the open amount, the revenue per payment method, and, if any invoice in the period has VAT, the gross, net, and VAT amounts per VAT rate.

#### Scenario: Paid and open
- **WHEN** a month has a paid invoice of 120.00 by card and an open invoice of 80.00
- **THEN** the report shows revenue 200.00, 2 invoices, paid 120.00, open 80.00, and card 120.00

### Requirement: Revenue per day
The report SHALL list every day of the period with its revenue and number of invoices, including days without revenue.

#### Scenario: Day without invoices
- **WHEN** no invoice was issued on Sunday 4 October 2026
- **THEN** the report lists 4 October with revenue 0.00 and 0 invoices

### Requirement: Revenue per service
The report SHALL list each service sold in the period with the quantity and the revenue, sorted by revenue, highest first. Services SHALL be shown with the name they had when sold. Sales of the same service under different names SHALL be listed separately.

#### Scenario: Top service
- **WHEN** in October "Facial" was sold 10 times at 120.00 and "Manicure" 15 times at 60.00
- **THEN** "Facial" is listed first with 10 and 1200.00, then "Manicure" with 15 and 900.00

### Requirement: Revenue per customer
The report SHALL list each customer with invoices in the period with customer number, name, number of invoices, and revenue, sorted by revenue, highest first. Anonymized customers SHALL be shown as "Deleted customer" with their number.

#### Scenario: Regular customer
- **WHEN** "Laura Meier" had 2 invoices of 120.00 and 50.00 in October
- **THEN** the October report lists the customer number, name, 2 invoices, and 170.00

### Requirement: Report on the devices
The report SHALL be available in the app on the web, Android, and iOS. It SHALL show the summary first and the lists for days, services, and customers in separate sections that can be opened one by one.

#### Scenario: Report on a phone
- **WHEN** an admin opens the October report on an Android phone
- **THEN** the summary is shown, and the admin can switch to the daily, service, and customer lists

### Requirement: Excel export
Admins SHALL be able to export the report of the chosen period as an Excel file (.xlsx) named `Rapport_<start>_<end>.xlsx`. It SHALL contain the sheets "Übersicht", "Tage", "Dienstleistungen", "Kunden", "Rechnungen" (every invoice of the period, including cancelled ones, with number, date, customer, total, VAT, status, payment method, and payment date), and "Positionen" (every invoiced line).

#### Scenario: Export October
- **WHEN** an admin exports the October 2026 report
- **THEN** the file "Rapport_2026-10-01_2026-10-31.xlsx" is downloaded with the six sheets

#### Scenario: Cancelled invoice in the export
- **WHEN** the period contains a cancelled invoice
- **THEN** it appears on the sheet "Rechnungen" with the status "storniert" and is not included in any total

### Requirement: Excel values are usable
In the Excel file, amounts SHALL be stored as numbers with two decimals, dates as dates, and each sheet SHALL have a header row. Totals SHALL match the report on the devices for the same period exactly.

#### Scenario: Sum in Excel
- **WHEN** a user sums the revenue column of the sheet "Tage" in Excel
- **THEN** the result equals the total revenue on the sheet "Übersicht" and in the app

### Requirement: Report access
Only admins SHALL be able to see the report and export it. The report SHALL be available only in salon mode.

#### Scenario: Staff cannot see the report
- **WHEN** a staff member requests the report or the Excel export
- **THEN** the system rejects the request as forbidden
