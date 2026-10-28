# Spec Delta

## Purpose

Delivers sent order rounds to the right production station. Drink lines print on the bar's Epson TM-m30III and food lines print on the kitchen's Epson TM-m30III. Failures are detected, retried, and visible to staff.

## ADDED Requirements

### Requirement: Printer configuration
Admins SHALL configure printers. Each printer has a name, a network address (IP or hostname), the ePOS device ID, an active flag, and the item type it serves (`food` or `drink`). Exactly one active printer SHALL be assigned to each item type. Admins SHALL be able to send a test print.

#### Scenario: Configure bar printer
- **WHEN** an admin adds the printer "Bar" at 192.168.1.50, assigned to `drink`
- **THEN** future drink lines are routed to "Bar"

#### Scenario: Test print
- **WHEN** an admin triggers a test print for "Kitchen"
- **THEN** the printer prints a test ticket, and the admin sees whether it succeeded or failed

#### Scenario: Missing routing
- **WHEN** no active printer is assigned to `food` and a round with food lines is sent
- **THEN** the round is still saved, the food ticket is marked failed with "no printer configured", and the waiter is warned

### Requirement: Route lines by type
For each sent round, the system SHALL create at most one ticket per printer. The drink printer's ticket SHALL contain only the round's drink lines, and the food printer's ticket SHALL contain only its food lines. No ticket SHALL be created for a type with no lines in the round.

#### Scenario: Mixed round
- **WHEN** a round contains 2 × "Cola" and 1 × "Margherita"
- **THEN** the bar printer prints one ticket with "2 × Cola", and the kitchen printer prints one ticket with "1 × Margherita"

#### Scenario: Drinks only
- **WHEN** a round contains only drinks
- **THEN** only the bar printer prints, and the kitchen printer receives nothing

### Requirement: Ticket content
Each production ticket SHALL show, in large readable text, the table label, the waiter name, the date and time sent, the round number, and each line with quantity, item name, and note. After printing, the ticket SHALL be cut. Prices are not required on production tickets.

#### Scenario: Ticket with note
- **WHEN** a line "1 × Margherita" with the note "extra spicy" is printed
- **THEN** the ticket shows the note directly below that line, together with the table, waiter, time, and round number

### Requirement: Cancellation tickets
When a sent line is cancelled, the system SHALL print a ticket marked clearly as a cancellation ("STORNO") on the printer of the line's type. The ticket SHALL show the table, the quantity, the item, and the reason.

#### Scenario: Cancel drink
- **WHEN** a sent "Beer" line is cancelled
- **THEN** the bar printer prints a STORNO ticket for "1 × Beer" with the table and reason

### Requirement: Print status, retry, and failure visibility
Each ticket SHALL have a status: `pending`, `printed`, or `failed`. The system SHALL retry failed deliveries automatically for at least 60 seconds. A ticket that still fails SHALL be marked `failed` and shown to waiters and admins with the printer's error (e.g. offline, paper end, cover open). Printing SHALL NOT block or undo saving the order.

#### Scenario: Printer offline
- **WHEN** the kitchen printer is unreachable while a round is sent
- **THEN** the order is saved, the system retries, and if it still fails the kitchen ticket is shown as failed with "printer offline" in the waiter app

#### Scenario: Paper end
- **WHEN** the printer reports paper end
- **THEN** the ticket is marked failed with "paper end"

### Requirement: Reprint
Waiters and admins SHALL be able to reprint any ticket. A reprinted ticket SHALL be marked "REPRINT" so it is not mistaken for a new order.

#### Scenario: Reprint after paper change
- **WHEN** a waiter reprints a failed kitchen ticket after the paper roll was replaced
- **THEN** the kitchen printer prints the same content marked "REPRINT", and the ticket status becomes `printed`

### Requirement: No duplicate production tickets
A ticket that was confirmed as printed SHALL NOT be printed again automatically. Only an explicit reprint prints it again.

#### Scenario: Retry after confirmed print
- **WHEN** the printer confirmed a ticket as printed
- **THEN** the retry mechanism does not send it again
