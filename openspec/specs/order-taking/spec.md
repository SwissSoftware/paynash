# order-taking Specification

## Purpose

Lets waiters record what guests order at a table: food and drink lines with quantities and notes. An order is sent in one go, which prints the kitchen and bar tickets and closes it. Sent orders are kept in an order history.

## Requirements

### Requirement: Create and send an order
A logged-in waiter SHALL be able to build an order and send it in one step. While `tables` is on, the order SHALL have a table label: picked from the active tables (using the table's number) or, as a fallback, entered as 1 to 10 alphanumeric characters. While `tables` is off, the order SHALL have no table label. Sending SHALL save the order with status `sent`, the sending waiter, and the time sent, and SHALL hand its lines to ticket printing. An order with no lines SHALL NOT be sent. A table MAY have any number of sent orders.

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
- **WHEN** `tables` is on and no active tables exist
- **THEN** the waiter sees an empty table list with the manual entry field and can send an order by typing a label

#### Scenario: Counter sale
- **WHEN** `tables` and `customers` are off and a staff member adds 2 × "Croissant" and sends
- **THEN** one order with status `sent` is saved without table label and without customer

#### Scenario: Missing table rejected
- **WHEN** `tables` is on and a send request has no table label
- **THEN** the system rejects it, and the draft stays on the screen

### Requirement: Order lines with food and drinks
An order SHALL contain any mix of food and drink lines. Each line references an active menu item and has a quantity from 1 to 99, an optional note of at most 140 characters (e.g. "no onions"), and the item's name, type, and unit price at the time it was sent. Before sending, lines can be added, changed, and removed freely.

#### Scenario: Add mixed items
- **WHEN** a waiter adds 2 × "Cola" and 1 × "Margherita" with the note "extra spicy"
- **THEN** the draft order shows both lines, with the note on the pizza and a correct running total

#### Scenario: Inactive item rejected
- **WHEN** an order is sent that contains an item which was deactivated in the meantime
- **THEN** the system rejects the whole order and names the inactive items, nothing is printed, and the app refreshes the menu and keeps the other lines in the draft

### Requirement: Sent orders are final
A sent order SHALL NOT accept new lines or changes to its lines. The only changes allowed are cancelling lines and voiding the order.

#### Scenario: Add to a sent order
- **WHEN** a request tries to add or change a line of a sent order
- **THEN** the system rejects it

### Requirement: Order total
The order total SHALL be the sum of quantity × unit price over all lines that are not cancelled. It is calculated in CHF with two decimals, without rounding errors.

#### Scenario: Total with cancellation
- **WHEN** an order has 2 × 4.50 and 1 × 18.00, and the 18.00 line is cancelled
- **THEN** the total is 9.00

### Requirement: Cancel sent lines
Admins, and stations with a staff PIN confirmation, SHALL be able to cancel a line of a sent order, with a required reason. The line SHALL stay in the order as cancelled, SHALL NOT count toward the total, and SHALL trigger a cancellation ticket on the printer that received the original line.

#### Scenario: Admin cancels a sent dish
- **WHEN** an admin cancels a sent "Margherita" with the reason "guest changed mind"
- **THEN** the line is marked cancelled, the total drops by its amount, and the kitchen printer prints a cancellation ticket

#### Scenario: Station cancels a drink with a PIN
- **WHEN** at the bar station, waiter "Anna" cancels a sent "Beer" with her PIN and the reason "sold out"
- **THEN** the line is marked cancelled by "Anna" at the bar station, and the bar printer prints a cancellation ticket

#### Scenario: Waiter cannot cancel
- **WHEN** a waiter tries to cancel a line of a sent order from their own device
- **THEN** the system rejects the request as forbidden

### Requirement: Void a sent order
An order SHALL be in exactly one state: `sent` or `voided`. Admins, and stations with a staff PIN confirmation, SHALL be able to void a sent order, with a required reason. Voiding SHALL cancel all of its lines that are not cancelled yet and SHALL print cancellation tickets for them.

#### Scenario: Admin voids an order
- **WHEN** an admin voids a sent order with 1 × "Beer" and 1 × "Margherita" with the reason "wrong table"
- **THEN** the order becomes `voided`, its total is 0.00, and the bar and kitchen printers each print a cancellation ticket

#### Scenario: Station voids an order with a PIN
- **WHEN** at the kitchen station, an active staff member voids a sent order with their PIN and the reason "double order"
- **THEN** the order becomes `voided`, cancellation tickets are printed, and the order records who voided it at which station

#### Scenario: Waiter cannot void
- **WHEN** a waiter tries to void an order from their own device
- **THEN** the system rejects the request as forbidden

### Requirement: Order history
Waiters and admins SHALL see an order history listing the orders sent on a chosen day (default today, in the restaurant's local time), newest first, with table label, waiter name, time sent, total, state, and print status. The print status SHALL distinguish waiting, unclear, and failed tickets. Opening an order SHALL show its lines with their status, and for cancelled lines who cancelled them and at which station. Changes from other devices SHALL appear within 5 seconds while the history is visible.

#### Scenario: Today's orders
- **WHEN** a waiter opens the order history
- **THEN** all orders sent today are listed newest first, with table, waiter, time, total, state, and print status

#### Scenario: Earlier day
- **WHEN** a waiter selects yesterday's date
- **THEN** the orders sent yesterday are listed

#### Scenario: New order from another device
- **WHEN** another waiter sends an order while the history is open
- **THEN** it appears in the list within 5 seconds

#### Scenario: Ticket waiting for paper
- **WHEN** an order's drink ticket waits because the bar printer has no paper
- **THEN** the history shows the order's print status as waiting with "paper end"

### Requirement: PIN confirmation for station cancellations
A cancellation or void from a station SHALL require the username and PIN of an active admin or waiter account. Station accounts SHALL NOT confirm. A wrong PIN SHALL reject the request without any change and SHALL count toward the same brute-force protection as login. The line or order SHALL record the confirming staff member and the station.

#### Scenario: Wrong PIN
- **WHEN** a cancellation at the bar station is confirmed with a wrong PIN
- **THEN** the system rejects it, the line stays sent, and no cancellation ticket is printed

#### Scenario: Deactivated account
- **WHEN** a cancellation is confirmed with the PIN of a deactivated account
- **THEN** the system rejects it

### Requirement: Clear message when sending may have failed
When sending an order fails because of the connection, the app SHALL tell the waiter that the order may or may not have arrived and that sending it again is safe and cannot create a second order.

#### Scenario: Connection lost while sending
- **WHEN** the connection drops while a waiter sends an order
- **THEN** the app shows that sending again is safe, and sending the unchanged draft again results in exactly one order

### Requirement: Confirm an order for a customer
While `customers` is on, staff and admins SHALL be able to pick a customer for an order from the active customers or create one on the spot. While `customers` is on and `tables` is off, every order SHALL have a customer. While both are on, the customer SHALL be optional. While `customers` is off, an order SHALL NOT have a customer.

#### Scenario: Confirm a treatment
- **WHEN** `tables` is off, `customers` is on, and a staff member picks "Laura Meier", adds 1 × "Facial", and confirms
- **THEN** one order with status `sent` is saved for "Laura Meier" with that staff member and time, without table label

#### Scenario: New customer at the counter
- **WHEN** a staff member creates the customer "Sara Keller" from the order screen
- **THEN** "Sara Keller" is stored and selected for the current order

#### Scenario: No customer rejected
- **WHEN** `tables` is off, `customers` is on, and a staff member tries to confirm an order without a customer
- **THEN** the system rejects it, and the draft stays on the screen

#### Scenario: Optional customer at a table
- **WHEN** `tables` and `customers` are on and a waiter sends an order for table "12" without a customer
- **THEN** the order is saved for table "12" without customer

#### Scenario: Inactive customer rejected
- **WHEN** an order is confirmed for a customer who was deactivated in the meantime
- **THEN** the system rejects it, names the customer, and the draft stays on the screen

#### Scenario: Double-tap confirm
- **WHEN** the same confirm request reaches the system twice
- **THEN** only one order is saved

### Requirement: Service lines
An order SHALL accept lines of any item type, so services, food, and drinks can be mixed. Each service line references an active service and has a quantity from 1 to 99, an optional note of at most 140 characters, and the service's name, type, unit price, and VAT rate at the time it was confirmed.

#### Scenario: Two services with a note
- **WHEN** a staff member adds 1 × "Facial" with the note "sensitive skin" and 2 × "Eyebrow tint"
- **THEN** the draft shows both lines, the note, and the running total

#### Scenario: Service and drink in one order
- **WHEN** a staff member adds 1 × "Haircut" and 1 × "Espresso" and confirms
- **THEN** both lines are saved in one order

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
