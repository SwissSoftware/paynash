# Spec Delta

## MODIFIED Requirements

### Requirement: Order history with customers and invoices
While `customers` is on, the order history SHALL show the customer name and number of each order that has a customer, next to its table label if it also has one. While `invoices` is on, it SHALL show the invoice status (`not invoiced`, the invoice number with `open` or `paid`, or cancelled) of every order, with or without a customer, and staff SHALL be able to issue the invoice of a not invoiced order from the history.

#### Scenario: Forgotten invoice
- **WHEN** a staff member opens today's order history and an order of "Laura Meier" is `not invoiced`
- **THEN** the order is shown with that status, and the staff member can issue its invoice from there

#### Scenario: Invoiced order
- **WHEN** an order has the paid invoice "2026-0042"
- **THEN** the history shows "2026-0042" and `paid` for this order

#### Scenario: Restaurant order for a company customer
- **WHEN** an order for table "12" also has the customer "Muster AG" and `invoices` is on
- **THEN** the history shows table "12", "Muster AG", and the invoice status

#### Scenario: Receipt for a table afterwards
- **WHEN** guests at table "7" ask for a receipt after their order without customer was sent
- **THEN** the waiter opens the order in the history and issues its invoice without recipient
