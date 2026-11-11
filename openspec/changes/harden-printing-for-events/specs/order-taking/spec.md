# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
