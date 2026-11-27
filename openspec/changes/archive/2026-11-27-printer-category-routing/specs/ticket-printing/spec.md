# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Route lines by type`
- TO: `### Requirement: Route lines by category`

## MODIFIED Requirements

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
In salon mode, the invoice view SHALL show, for each printer that received the invoice's receipt, the status of its latest receipt job and the printer's error if it is waiting, unclear, or failed. Staff and admins SHALL be able to print a copy from there.

#### Scenario: Receipt failed
- **WHEN** the receipt of invoice "2026-0042" failed with "no active printer"
- **THEN** the invoice view shows the error, and after an admin activated a receipt printer, staff can print a copy

#### Scenario: One of two receipts unclear
- **WHEN** the receipt of an invoice printed on "Counter" but is `unclear` on "Office"
- **THEN** the invoice view shows "Counter" as printed and "Office" as unclear

## ADDED Requirements

### Requirement: Printers and category routing
Admins SHALL configure printers. Each printer has a name, a network address (IP or hostname), the ePOS device ID, an active flag, a "tickets" switch with the menu categories whose lines it prints, and a "receipts" switch for invoice receipts. A category MAY be assigned to several printers, and several printers MAY have receipts on. Both switches SHALL be available in every venue mode. Admins SHALL be able to send a test print.

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
- **WHEN** an admin in salon mode adds the printer "Counter" at 192.168.1.60 with receipts on and tickets off
- **THEN** the printer is saved, and future invoice receipts are printed on "Counter"

#### Scenario: Second active receipt printer accepted
- **WHEN** an admin activates a second printer with receipts on
- **THEN** the system accepts it, and both printers print future receipts

#### Scenario: Ticket printer in salon mode
- **WHEN** an admin in salon mode adds a printer with tickets on and the category "Hair"
- **THEN** the printer is saved without a mode error

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

## REMOVED Requirements

### Requirement: Printer configuration
**Reason**: Replaced by category routing. A printer now has a "tickets" switch with the categories it prints and a "receipts" switch instead of one item type, and several active receipt printers are allowed, so the former scenario "Second active receipt printer rejected" no longer holds.
**Migration**: See "Printers and category routing" in this capability. Migration V15 converts each printer's item type into category assignments and its receipt purpose into the "receipts" switch.

### Requirement: No production tickets for services
**Reason**: Whether a line is printed now depends only on its category's printer assignment, not on the item type.
**Migration**: A salon that does not want service tickets simply assigns no printer to its service categories. The migration assigns no categories to existing receipt printers, so salons keep printing no tickets.
