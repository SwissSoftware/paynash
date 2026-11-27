# Spec Delta

## MODIFIED Requirements

### Requirement: Revenue per customer
The report SHALL list each customer with invoices in the period with customer number, name, number of invoices, and revenue, sorted by revenue, highest first. Anonymized customers SHALL be shown as "Deleted customer" with their number. Invoices without customer SHALL be listed together as one entry "Ohne Kunde" without number, sorted by revenue like a customer.

#### Scenario: Regular customer
- **WHEN** "Laura Meier" had 2 invoices of 120.00 and 50.00 in October
- **THEN** the October report lists the customer number, name, 2 invoices, and 170.00

#### Scenario: Counter sales without customer
- **WHEN** in October 30 invoices without customer total 845.50 and "Laura Meier" had invoices of 170.00
- **THEN** the October report lists "Ohne Kunde" with 30 invoices and 845.50 first, then "Laura Meier", and the report total includes both
