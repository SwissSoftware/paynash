# ticket-printing Specification

## Purpose

Delivers sent orders to the right production station. Drink lines print on the bar's Epson TM-m30III and food lines print on the kitchen's Epson TM-m30III. Failures are detected, retried, and visible to staff.

## Requirements

### Requirement: Route lines by category
For each sent order, the system SHALL create at most one ticket per printer, holding the order's lines whose category is assigned to that printer. A line SHALL be printed on every active printer that has tickets on and its category assigned. A line whose category is assigned to no printer SHALL NOT be printed, and no error SHALL be shown. If all printers assigned to a line's category are inactive, the system SHALL create a `failed` ticket "no active printer" for each of them.

#### Scenario: Mixed order
- **WHEN** "Soft drinks" is assigned to "Bar", "Pizza" to "Kitchen", and an order contains 2 × "Cola" and 1 × "Margherita"
- **THEN** "Bar" prints one ticket with "2 × Cola", and "Kitchen" prints one ticket with "1 × Margherita"

#### Scenario: Drinks only
- **WHEN** an order contains only lines of categories assigned to "Bar"
- **THEN** only "Bar" prints, and "Kitchen" receives nothing

#### Scenario: Category on two printers
- **WHEN** "Pizza" is assigned to "Kitchen" and "Pass", both active, and an order with 1 × "Margherita" is sent
- **THEN** "Kitchen" and "Pass" each print a ticket with "1 × Margherita"

#### Scenario: Category not printed
- **WHEN** the category "Entrance" is assigned to no printer and an order with 1 × "Entry fee" and 1 × "Cola" is sent
- **THEN** only the "Cola" line is printed, no ticket is created for "Entry fee", and no warning is shown

#### Scenario: Assigned printer inactive
- **WHEN** "Soft drinks" is assigned only to "Bar", "Bar" is inactive, and an order with 1 × "Cola" is sent
- **THEN** the order is still saved, a ticket for "Bar" is marked `failed` with "no active printer", and the waiter is warned

#### Scenario: One of two printers inactive
- **WHEN** "Pizza" is assigned to "Kitchen" (active) and "Pass" (inactive), and an order with 1 × "Margherita" is sent
- **THEN** only "Kitchen" prints the line, and no failed ticket is created

#### Scenario: Printer with tickets off
- **WHEN** "Pizza" is assigned to "Kitchen", "Kitchen" is active but its tickets switch is off, and an order with 1 × "Margherita" is sent
- **THEN** the line is treated as not printed: no ticket and no error

### Requirement: Ticket content
Each production ticket SHALL show, in large readable text, the printer's ticket number, the table label, the waiter name, the date and time sent, the order number, and each line with quantity, item name, and note. After printing, the ticket SHALL be cut. Prices are not required on production tickets.

#### Scenario: Ticket with note
- **WHEN** a line "1 × Margherita" with the note "extra spicy" is printed
- **THEN** the ticket shows the note directly below that line, together with the ticket number, table, waiter, time, and order number

### Requirement: Cancellation tickets
When a sent line is cancelled, including when its order is voided, the system SHALL print a ticket marked clearly as a cancellation ("STORNO") on each printer the line was routed to when it was sent, following the same rules for inactive printers as sent orders. Changing a category's printers later SHALL NOT change where its sent lines are cancelled. The ticket SHALL show the table, the order number, the quantity, the item, and the reason. Voiding an order SHALL create at most one cancellation ticket per printer.

#### Scenario: Cancel drink
- **WHEN** a sent "Beer" line is cancelled
- **THEN** the bar printer prints a STORNO ticket for "1 × Beer" with the table and reason

#### Scenario: Cancel line printed on two printers
- **WHEN** a sent "Margherita" line that was printed on "Kitchen" and "Pass" is cancelled
- **THEN** both "Kitchen" and "Pass" print a STORNO ticket for it

#### Scenario: Assignment changed after sending
- **WHEN** a "Margherita" line was sent while "Pizza" was assigned to "Kitchen", "Pizza" is then moved to "Pass", and the line is cancelled
- **THEN** the STORNO ticket is printed on "Kitchen"

#### Scenario: Cancel a line that was not printed
- **WHEN** a sent line of a category that was assigned to no printer is cancelled
- **THEN** no STORNO ticket is created

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

#### Scenario: Paper end
- **WHEN** the printer reports paper end
- **THEN** the ticket stays `pending` with the reason "paper end" and is printed automatically once paper is loaded

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

### Requirement: Receipt print jobs
Invoice receipts SHALL be print jobs with the same statuses and guarantees as production tickets: they wait for the printer instead of failing, are not printed twice automatically, and become `unclear` if the system restarts while printing. Each receipt SHALL be printed once on every active printer with receipts on, as a separate job per printer. If printers have receipts on but all are inactive, a `failed` job "no active printer" SHALL be created for each of them. If no printer has receipts on, no receipt job SHALL be created.

#### Scenario: Receipt printer out of paper
- **WHEN** an invoice is issued while the receipt printer has no paper, and the roll is replaced 3 minutes later
- **THEN** the receipt prints by itself once the paper is in

#### Scenario: Unclear receipt
- **WHEN** the receipt printer accepted a receipt but did not answer
- **THEN** the receipt is `unclear`, is not printed again automatically, and the invoice shows "check the receipt and print a copy if needed"

#### Scenario: Two receipt printers
- **WHEN** "Counter" and "Office" are active with receipts on and an invoice is issued
- **THEN** each of them prints the receipt once

#### Scenario: Receipt printer inactive
- **WHEN** only "Counter" has receipts on, "Counter" is inactive, and an invoice is issued
- **THEN** the invoice is issued, and its receipt job for "Counter" is `failed` with "no active printer"

#### Scenario: No printer with receipts on
- **WHEN** no printer has receipts on and an invoice is issued
- **THEN** the invoice is issued, and no receipt job is created

### Requirement: Receipt status on the invoice
While `invoices` is on, the invoice view SHALL show, for each printer that received the invoice's receipt, the status of its latest receipt job and the printer's error if it is waiting, unclear, or failed. Staff and admins SHALL be able to print a copy from there.

#### Scenario: Receipt failed
- **WHEN** the receipt of invoice "2026-0042" failed with "no active printer"
- **THEN** the invoice view shows the error, and after an admin activated a receipt printer, staff can print a copy

#### Scenario: One of two receipts unclear
- **WHEN** the receipt of an invoice printed on "Counter" but is `unclear` on "Office"
- **THEN** the invoice view shows "Counter" as printed and "Office" as unclear

### Requirement: Receipt content
A receipt SHALL contain the full invoice content in a layout for 80 mm paper: business details, invoice number and date, customer, lines with quantity, name, and amount, total, and the VAT breakdown and UID if the invoice has VAT. After printing, the paper SHALL be cut.

#### Scenario: Receipt with VAT
- **WHEN** the receipt of an invoice with VAT is printed
- **THEN** it shows the UID and the gross, net, and VAT amounts per rate below the total

### Requirement: Printers and category routing
Admins SHALL configure printers. Each printer has a name, a network address (IP or hostname), the ePOS device ID, an active flag, a "tickets" switch with the menu categories whose lines it prints, and a "receipts" switch for invoice receipts. A category MAY be assigned to several printers, and several printers MAY have receipts on. The "tickets" switch SHALL be available with any features. The "receipts" switch SHALL be shown only while `invoices` is on. Admins SHALL be able to send a test print.

#### Scenario: Configure bar printer
- **WHEN** an admin adds the printer "Bar" at 192.168.1.50 with tickets on and the categories "Beer" and "Soft drinks"
- **THEN** future lines of "Beer" and "Soft drinks" are routed to "Bar"

#### Scenario: Test print
- **WHEN** an admin triggers a test print for "Kitchen"
- **THEN** the printer prints a test ticket, and the admin sees whether it succeeded or failed

#### Scenario: Missing routing
- **WHEN** the category "Pizza" is assigned only to inactive printers and an order with food lines of "Pizza" is sent
- **THEN** the order is still saved, the tickets for those printers are marked failed with "no active printer", and the waiter is warned

#### Scenario: Category on two printers
- **WHEN** an admin assigns the category "Pizza" to both "Kitchen" and "Pass"
- **THEN** the system accepts it, and both printers list "Pizza"

#### Scenario: Configure a salon receipt printer
- **WHEN** an admin of a salon with `invoices` on adds the printer "Counter" at 192.168.1.60 with receipts on and tickets off
- **THEN** the printer is saved, and future invoice receipts are printed on "Counter"

#### Scenario: Second active receipt printer accepted
- **WHEN** an admin activates a second printer with receipts on
- **THEN** the system accepts it, and both printers print future receipts

#### Scenario: Ticket printer in salon mode
- **WHEN** an admin of a salon with `tables` off adds a printer with tickets on and the category "Hair"
- **THEN** the printer is saved, and lines of "Hair" print tickets on it

#### Scenario: Deleted category
- **WHEN** an admin deletes the category "Specials", which was assigned to "Kitchen"
- **THEN** "Kitchen" no longer lists "Specials", and the printer is otherwise unchanged

### Requirement: Categories that are not printed
The printer configuration SHALL show the menu categories that no active printer with tickets on prints, as neutral information and not as an error.

#### Scenario: Unassigned category listed
- **WHEN** the category "Entrance" is assigned to no printer
- **THEN** the printer page lists "Entrance" under the categories that are not printed

#### Scenario: Category only on inactive printer
- **WHEN** "Soft drinks" is assigned only to the inactive printer "Bar"
- **THEN** the printer page lists "Soft drinks" under the categories that are not printed
