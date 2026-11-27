# Spec Delta

## Purpose

Gives admins a CSV export of the orders placed in a chosen period, so sales can be analyzed in spreadsheet or accounting tools.

## ADDED Requirements

### Requirement: Export orders as CSV
Admins SHALL be able to download a CSV file of orders in an inclusive date range, based on the date the order was sent in the restaurant's local time zone. They SHALL be able to filter by order state (`sent`, `voided`, or all). Sent orders is the default.

#### Scenario: Export one day
- **WHEN** an admin exports sent orders from 2026-10-01 to 2026-10-01
- **THEN** a CSV file is downloaded that contains exactly the lines of the sent orders sent on that day

#### Scenario: Empty period
- **WHEN** no orders match the filter
- **THEN** a CSV file is downloaded that contains only the header row

#### Scenario: Invalid range
- **WHEN** the start date is after the end date
- **THEN** the system rejects the request with a validation error

### Requirement: CSV layout
The export SHALL have one row per order line, with these columns in this order: `order_id`, `table`, `waiter`, `sent_at`, `order_status`, `item_name`, `item_type`, `category`, `quantity`, `unit_price`, `line_total`, `line_status`, `note`. Timestamps SHALL be ISO 8601 in local time with offset. Amounts SHALL use a dot as the decimal separator with two decimals.

#### Scenario: Row content
- **WHEN** a sent order for table "12" contains 2 × "Cola" at 4.50
- **THEN** the CSV contains a row with `table` 12, `quantity` 2, `unit_price` 4.50, `line_total` 9.00, and `item_type` drink

#### Scenario: Cancelled line
- **WHEN** an exported order contains a cancelled line
- **THEN** the row appears with `line_status` cancelled and `line_total` 0.00

### Requirement: CSV encoding and escaping
The file SHALL be UTF-8 with a byte-order mark (BOM) so Excel shows umlauts correctly. It SHALL use a comma as the separator and quote and escape fields according to RFC 4180. Its filename SHALL contain the date range.

#### Scenario: Special characters in notes
- **WHEN** a note contains a comma, a quote, or a line break (e.g. `ohne "Zwiebeln", bitte`)
- **THEN** the field is correctly quoted, and the file opens in Excel with the columns intact

#### Scenario: Umlauts
- **WHEN** an item is called "Rösti"
- **THEN** it appears correctly as "Rösti" when the file is opened in Excel

### Requirement: Export access control
Only admins SHALL be able to request exports.

#### Scenario: Waiter blocked
- **WHEN** a waiter requests an export
- **THEN** the system rejects the request as forbidden
