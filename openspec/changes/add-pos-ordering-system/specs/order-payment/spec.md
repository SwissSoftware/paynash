# Spec Delta

## Purpose

Lets waiters settle an open order in full by cash or card and close it. The payment method and amount are recorded for reporting and export.

## ADDED Requirements

### Requirement: Order total
The order total SHALL be the sum of quantity × unit price over all lines that are not cancelled. It is calculated in CHF with two decimals, without rounding errors.

#### Scenario: Total with cancellation
- **WHEN** an order has 2 × 4.50 and 1 × 18.00, and the 18.00 line is cancelled
- **THEN** the total is 9.00

### Requirement: Settle order
A waiter SHALL be able to settle an open order in full, with the method `cash` or `card`. Settling SHALL record the method, the amount (equal to the total), the waiter, and a timestamp, and SHALL set the order to `paid`. Partial or split payments are out of scope.

#### Scenario: Card payment
- **WHEN** a waiter settles an order with a total of 27.00 by `card`
- **THEN** a payment of 27.00 by card is recorded, and the order becomes `paid`

#### Scenario: Cash with change
- **WHEN** a waiter settles an order with a total of 27.00 by `cash` and enters 50.00 received
- **THEN** the app shows 23.00 change, a payment of 27.00 by cash is recorded, and the order becomes `paid`

#### Scenario: Insufficient cash entered
- **WHEN** the cash received is less than the total
- **THEN** the system rejects the payment, and the order stays `open`

### Requirement: Settlement preconditions
The system SHALL reject settling an order that is not `open`, that has unsent lines, or whose total is 0.00. A settlement request that is repeated, for example after a network retry, SHALL NOT record a second payment.

#### Scenario: Unsent lines
- **WHEN** a waiter tries to settle an order that still has unsent lines
- **THEN** the system rejects it and asks the waiter to send or remove them first

#### Scenario: Already paid
- **WHEN** a settle request arrives for an order that is already `paid`
- **THEN** no second payment is recorded

### Requirement: Paid orders are read-only
A `paid` order SHALL NOT accept new lines or cancellations. An admin SHALL be able to reopen a paid order with a required reason. Reopening deletes no history: the original payment is marked reversed, and the reason and admin are recorded.

#### Scenario: Add to paid order
- **WHEN** a waiter tries to add a line to a `paid` order
- **THEN** the system rejects it

#### Scenario: Admin reopens
- **WHEN** an admin reopens a paid order with the reason "wrong payment method"
- **THEN** the order becomes `open`, the payment is marked reversed with the reason, and the order can be settled again
