# Spec Delta

## Purpose

Lets waiters record what guests order at a table: food and drink lines with quantities and notes. Orders are sent in rounds so the kitchen and bar receive tickets, and the order stays open until it is paid.

## ADDED Requirements

### Requirement: Open an order for a table
A logged-in waiter SHALL be able to open an order by entering a table label of 1 to 10 alphanumeric characters. Each table label SHALL have at most one open order at a time. The order SHALL record the waiter who opened it and the opening time.

#### Scenario: Open new order
- **WHEN** a waiter opens an order for table "12" and table "12" has no open order
- **THEN** an empty open order for table "12" is created and assigned to that waiter

#### Scenario: Table already has an open order
- **WHEN** a waiter opens an order for a table that already has an open order
- **THEN** the system opens the existing order instead of creating a second one

### Requirement: Order lines with food and drinks
An order SHALL contain any mix of food and drink lines. Each line references an active menu item and has a quantity from 1 to 99, an optional note of at most 140 characters (e.g. "no onions"), and the item's name, type, and unit price at the time it was added.

#### Scenario: Add mixed items
- **WHEN** a waiter adds 2 × "Cola" and 1 × "Margherita" with the note "extra spicy"
- **THEN** the order shows both lines, with the note on the pizza and a correct running total

#### Scenario: Inactive item rejected
- **WHEN** the app tries to add an item that was deactivated in the meantime
- **THEN** the system rejects the line, and the app refreshes the menu

### Requirement: Send order round
Lines start out as unsent and can be edited or removed freely. When the waiter sends the order, all unsent lines SHALL be marked as sent with a timestamp, and SHALL be handed to ticket printing as one round. Sending with no unsent lines SHALL do nothing.

#### Scenario: Send first round
- **WHEN** a waiter sends an order with 3 unsent lines
- **THEN** the 3 lines become sent, and one print round is created that contains exactly those lines

#### Scenario: Additional round
- **WHEN** the waiter later adds 1 × "Beer" to the same order and sends again
- **THEN** only the new "Beer" line is printed, not the earlier lines

#### Scenario: Double-tap send
- **WHEN** the same send request reaches the system twice, for example after a network retry
- **THEN** only one print round is created

### Requirement: Cancel sent lines
A sent line SHALL NOT be edited. Waiters SHALL be able to cancel a sent line with a required reason. The line SHALL stay in the order as cancelled, SHALL NOT count toward the total, and SHALL trigger a cancellation ticket on the printer that received the original line.

#### Scenario: Cancel a sent dish
- **WHEN** a waiter cancels a sent "Margherita" with the reason "guest changed mind"
- **THEN** the line is marked cancelled, the total drops by its amount, and the kitchen printer prints a cancellation ticket

### Requirement: Order overview and states
An order SHALL be in exactly one state: `open`, `paid`, or `voided`. Waiters SHALL see a list of all open orders with table, waiter, total, and age. An admin SHALL be able to void an open order that has no paid amount, with a reason.

#### Scenario: Open orders list
- **WHEN** a waiter opens the order overview
- **THEN** all `open` orders are listed with table label, waiter name, current total, and minutes since opening

#### Scenario: Waiter cannot void
- **WHEN** a waiter tries to void an order
- **THEN** the system rejects the request as forbidden

### Requirement: Concurrent editing
Several devices SHALL be able to work on the same open order. Changes from one device SHALL be visible on other devices within 5 seconds, or immediately after a refresh. Conflicting writes SHALL NOT silently overwrite or lose lines.

#### Scenario: Two waiters add to the same table
- **WHEN** two waiters each add a line to the order for table "5" at the same time
- **THEN** both lines exist in the order afterwards
