# Spec Delta

## MODIFIED Requirements

### Requirement: Create and send an order
In event mode, a logged-in waiter SHALL be able to build an order for a table and send it in one step. The waiter SHALL pick the table from the list of active tables or, as a fallback, enter a table label of 1 to 10 alphanumeric characters. A picked table SHALL use the table's number as the order's table label. Sending SHALL save the order with status `sent`, the table label, the sending waiter, and the time sent, and SHALL hand all its lines to ticket printing. An order with no lines SHALL NOT be sent. A table MAY have any number of sent orders.

#### Scenario: Send an order
- **WHEN** a waiter builds an order for table "12" with 3 lines and sends it
- **THEN** one order with status `sent` and the 3 lines is saved for table "12" with that waiter and time, and its tickets are printed

#### Scenario: Order screen resets after sending
- **WHEN** the order was sent successfully
- **THEN** the order screen is empty and ready for the next order, and the sent order appears in the order history

#### Scenario: Follow-up order for the same table
- **WHEN** guests at table "12" order another "Beer" after their first order was sent
- **THEN** the waiter sends a new order for table "12" with only the "Beer", and only the "Beer" is printed

#### Scenario: Empty order
- **WHEN** a waiter tries to send an order without lines
- **THEN** the system rejects it and nothing is saved or printed

#### Scenario: Double-tap send
- **WHEN** the same send request reaches the system twice, for example after a network retry
- **THEN** only one order is saved and its tickets are printed once

#### Scenario: Send fails
- **WHEN** sending fails because there is no connection
- **THEN** the draft order stays on the screen unchanged so the waiter can send it again

#### Scenario: Pick a table from the list
- **WHEN** a waiter picks table "12" ("Terrace window") from the table list, adds items, and sends
- **THEN** the order is saved with table label "12"

#### Scenario: Manual entry matching a configured table
- **WHEN** a waiter types "12" and table "12" exists
- **THEN** the order is sent exactly as when picking table "12" from the list

#### Scenario: Manual entry without a configured table
- **WHEN** a waiter types "99" and no table "99" exists
- **THEN** the order is sent with table label "99", as without table management

#### Scenario: No tables configured
- **WHEN** no active tables exist
- **THEN** the waiter sees an empty table list with the manual entry field and can send an order by typing a label

## ADDED Requirements

### Requirement: Confirm an order for a customer
In salon mode, staff and admins SHALL build an order for a customer and confirm it in one step. The customer SHALL be picked from the active customers or created on the spot. Confirming SHALL save the order with status `sent`, the customer, the staff member, and the time, without a table label. An order without customer or without lines SHALL NOT be confirmed.

#### Scenario: Confirm a treatment
- **WHEN** a staff member picks "Laura Meier", adds 1 × "Facial", and confirms
- **THEN** one order with status `sent` is saved for "Laura Meier" with that staff member and time, and no production ticket is printed

#### Scenario: New customer at the counter
- **WHEN** a staff member creates the customer "Sara Keller" from the order screen
- **THEN** "Sara Keller" is stored and selected for the current order

#### Scenario: No customer rejected
- **WHEN** a staff member tries to confirm an order without a customer
- **THEN** the system rejects it, and the draft stays on the screen

#### Scenario: Inactive customer rejected
- **WHEN** an order is confirmed for a customer who was deactivated in the meantime
- **THEN** the system rejects it, names the customer, and the draft stays on the screen

#### Scenario: Double-tap confirm
- **WHEN** the same confirm request reaches the system twice
- **THEN** only one order is saved

### Requirement: Service lines
In salon mode, an order SHALL contain only service lines. Each line references an active service and has a quantity from 1 to 99, an optional note of at most 140 characters, and the service's name, type, unit price, and VAT rate at the time it was confirmed.

#### Scenario: Two services with a note
- **WHEN** a staff member adds 1 × "Facial" with the note "sensitive skin" and 2 × "Eyebrow tint"
- **THEN** the draft shows both lines, the note, and the running total

### Requirement: Order history in salon mode
In salon mode, the order history SHALL show the customer name and number instead of the table label, and the invoice status (`not invoiced`, the invoice number with `open` or `paid`, or cancelled) instead of the print status. Staff SHALL be able to issue the invoice of a not invoiced order from the history.

#### Scenario: Forgotten invoice
- **WHEN** a staff member opens today's order history and an order of "Laura Meier" is `not invoiced`
- **THEN** the order is shown with that status, and the staff member can issue its invoice from there

#### Scenario: Invoiced order
- **WHEN** an order has the paid invoice "2026-0042"
- **THEN** the history shows "2026-0042" and `paid` for this order
