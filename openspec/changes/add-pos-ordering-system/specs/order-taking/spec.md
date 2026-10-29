# Spec Delta

## Purpose

Lets waiters record what guests order at a table: food and drink lines with quantities and notes. An order is sent in one go, which prints the kitchen and bar tickets and closes it. Sent orders are kept in an order history.

## ADDED Requirements

### Requirement: Create and send an order
A logged-in waiter SHALL be able to build an order for a table label of 1 to 10 alphanumeric characters and send it in one step. Sending SHALL save the order with status `sent`, the table label, the sending waiter, and the time sent, and SHALL hand all its lines to ticket printing. An order with no lines SHALL NOT be sent. A table MAY have any number of sent orders.

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
Only admins SHALL be able to cancel a line of a sent order, with a required reason. The line SHALL stay in the order as cancelled, SHALL NOT count toward the total, and SHALL trigger a cancellation ticket on the printer that received the original line.

#### Scenario: Admin cancels a sent dish
- **WHEN** an admin cancels a sent "Margherita" with the reason "guest changed mind"
- **THEN** the line is marked cancelled, the total drops by its amount, and the kitchen printer prints a cancellation ticket

#### Scenario: Waiter cannot cancel
- **WHEN** a waiter tries to cancel a line of a sent order
- **THEN** the system rejects the request as forbidden

### Requirement: Void a sent order
An order SHALL be in exactly one state: `sent` or `voided`. Only admins SHALL be able to void a sent order, with a required reason. Voiding SHALL cancel all of its lines that are not cancelled yet and SHALL print cancellation tickets for them.

#### Scenario: Admin voids an order
- **WHEN** an admin voids a sent order with 1 × "Beer" and 1 × "Margherita" with the reason "wrong table"
- **THEN** the order becomes `voided`, its total is 0.00, and the bar and kitchen printers each print a cancellation ticket

#### Scenario: Waiter cannot void
- **WHEN** a waiter tries to void an order
- **THEN** the system rejects the request as forbidden

### Requirement: Order history
Waiters and admins SHALL see an order history listing the orders sent on a chosen day (default today, in the restaurant's local time), newest first, with table label, waiter name, time sent, total, state, and print status. Opening an order SHALL show its lines with their status. Changes from other devices SHALL appear within 5 seconds while the history is visible.

#### Scenario: Today's orders
- **WHEN** a waiter opens the order history
- **THEN** all orders sent today are listed newest first, with table, waiter, time, total, state, and print status

#### Scenario: Earlier day
- **WHEN** a waiter selects yesterday's date
- **THEN** the orders sent yesterday are listed

#### Scenario: New order from another device
- **WHEN** another waiter sends an order while the history is open
- **THEN** it appears in the list within 5 seconds
