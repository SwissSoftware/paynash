# Spec Delta

## MODIFIED Requirements

### Requirement: Ticket content
Each production ticket SHALL show, in large readable text, the printer's ticket number, the table label, the waiter name, the date and time sent, the order number, and each line with quantity, item name, and note. After printing, the ticket SHALL be cut. Prices are not required on production tickets.

#### Scenario: Ticket with note
- **WHEN** a line "1 × Margherita" with the note "extra spicy" is printed
- **THEN** the ticket shows the note directly below that line, together with the ticket number, table, waiter, time, and order number

### Requirement: Print status, retry, and failure visibility
Each ticket SHALL have a status: `pending`, `printing`, `printed`, `unclear`, or `failed`. A ticket SHALL be `unclear` when the printer may have printed it, and `failed` only when it certainly did not print and cannot print without a configuration change. Unclear and failed tickets SHALL be shown to waiters, admins, and the stations with the printer's error. Printing SHALL NOT block or undo saving the order.

#### Scenario: Printer offline
- **WHEN** the kitchen printer is unreachable while an order is sent
- **THEN** the order is saved, the kitchen ticket stays `pending` with the reason "printer offline", and the kitchen station shows it as waiting

#### Scenario: No answer after sending
- **WHEN** the printer accepted the ticket data but did not answer
- **THEN** the ticket is marked `unclear` with "check the ticket and reprint if needed" and is not sent again automatically

#### Scenario: Wrong device ID
- **WHEN** the printer reports that the configured device ID does not exist
- **THEN** the ticket is marked `failed` with "device ID not found on the printer"

### Requirement: Reprint
Waiters, admins, and stations SHALL be able to reprint any ticket that is not `pending` or `printing`. A reprinted ticket SHALL be marked "REPRINT" and keep the original ticket number, so it is not mistaken for a new order.

#### Scenario: Reprint after paper change
- **WHEN** a waiter reprints a failed kitchen ticket after the problem was fixed
- **THEN** the kitchen printer prints the same content marked "REPRINT" with the original ticket number, and the reprint's status becomes `printed`

### Requirement: No duplicate production tickets
A ticket that was confirmed as printed, or whose outcome is unclear, SHALL NOT be printed again automatically. Only an explicit reprint or the "reprint" resolution of an unclear ticket prints it again.

#### Scenario: Retry after confirmed print
- **WHEN** the printer confirmed a ticket as printed
- **THEN** the retry mechanism does not send it again

#### Scenario: Unclear ticket
- **WHEN** a ticket's outcome is unclear
- **THEN** it is not sent again until staff choose "reprint"

## ADDED Requirements

### Requirement: Wait for the printer instead of failing
When a ticket certainly did not print because the printer is offline, out of paper, or has its cover open, the ticket SHALL stay `pending` and be retried without a time limit, with pauses of at most 16 seconds. Later tickets for the same printer SHALL wait behind it and SHALL print in their original order once the printer is ready.

#### Scenario: Long paper change
- **WHEN** the bar printer runs out of paper and the roll is replaced after 4 minutes, during which 5 drink tickets were created
- **THEN** none of them is marked failed, and all 5 print by themselves in their original order once the paper is in

#### Scenario: Other printer unaffected
- **WHEN** the bar printer is offline
- **THEN** food tickets still print on the kitchen printer without delay

### Requirement: Crash-safe delivery
A ticket SHALL be marked `printing` before it is sent to the printer. A ticket that is still `printing` when the system starts SHALL be marked `unclear` with "system restarted while printing" and SHALL NOT be sent again automatically.

#### Scenario: Backend restarts while printing
- **WHEN** the system stops after sending a ticket to the printer but before the printer's answer was recorded
- **THEN** after the restart the ticket is `unclear` and shown at the station, and it is not printed a second time

### Requirement: Running ticket number per printer
Each ticket SHALL get the next number of its printer's counter when it is created, so numbers follow the printing order. Cancellation tickets SHALL use the same counter. The ticket number SHALL be shown with the printer's name, for example "BAR 0142".

#### Scenario: Consecutive numbers
- **WHEN** three drink tickets are created one after another
- **THEN** they carry three consecutive bar ticket numbers, and the kitchen counter is unchanged

#### Scenario: Cancellation numbered
- **WHEN** a drink line is cancelled after ticket "BAR 0142"
- **THEN** the STORNO ticket carries "BAR 0143"

### Requirement: Printer status monitoring
The system SHALL check each active printer's status at least every 15 seconds while it has no ticket to send, and SHALL keep the latest result: ready, offline, paper end, cover open, or paper near end. Offline SHALL always be detected. The other states SHALL be reported when the printer provides them.

#### Scenario: Idle printer unplugged
- **WHEN** the kitchen printer loses its connection while no ticket is waiting
- **THEN** within 15 seconds its status is offline, and admins and the kitchen station see it

### Requirement: Printers on WiFi
Printers connected over WiFi SHALL be supported with the same statuses and the same guarantees as wired printers.

#### Scenario: WiFi drops while sending
- **WHEN** the connection to a WiFi printer drops after the ticket data was sent
- **THEN** the ticket is marked `unclear` and shown at the station, and it is not printed twice automatically

#### Scenario: WiFi drops before sending
- **WHEN** a WiFi printer cannot be reached before the ticket is sent
- **THEN** the ticket stays `pending` and prints by itself once the printer is reachable again
