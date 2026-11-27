# Spec Delta

## Purpose

Gives the staff next to each printer a live view of that printer's tickets and status, alerts them when a ticket or the printer needs attention, and lets them resolve unclear tickets, so no ticket is lost or duplicated unnoticed.

## ADDED Requirements

### Requirement: Station ticket list
A logged-in station SHALL see the tickets of its assigned printers for the current day, newest first. Each entry SHALL show the time created, the printer's ticket number, the order number, the table label, the waiter name, the number of lines, the kind (order, cancellation, or reprint), and the print status. Changes SHALL appear within 5 seconds while the list is visible.

#### Scenario: New order appears at the bar station
- **WHEN** waiter "Anna" sends an order for table "12" with 2 drinks
- **THEN** within 5 seconds the bar station lists the ticket with its ticket number, the order number, table "12", "Anna", 2 lines, and its status

#### Scenario: Station only sees its printers
- **WHEN** an order with food and drinks is sent
- **THEN** the bar station lists only the drink ticket and the kitchen station lists only the food ticket

### Requirement: Printer status at the station
The station SHALL show the current status of each assigned printer: ready, offline, paper end, cover open, or paper near end. Offline SHALL always be detected. Paper end, cover open, and paper near end SHALL be shown when the printer reports them. The status SHALL be updated within 15 seconds, also when no ticket is waiting.

#### Scenario: Printer switched off while idle
- **WHEN** the bar printer is switched off and no ticket is waiting
- **THEN** within 15 seconds the bar station shows the printer as offline

#### Scenario: Paper replaced
- **WHEN** the paper roll is replaced after a paper end
- **THEN** the station shows the printer as ready again within 15 seconds

### Requirement: Station alerts
The station SHALL raise a visible and audible alert while any of these holds: an assigned printer is not ready, a ticket is unclear, the oldest waiting ticket has waited more than 30 seconds, or the station has lost its connection to the system. The alert SHALL name the cause and SHALL end by itself once no cause remains.

#### Scenario: Paper end during service
- **WHEN** the bar printer reports paper end while 3 tickets are waiting
- **THEN** the bar station shows a red alert "paper end, 3 tickets waiting" with a sound until the printer is ready again

#### Scenario: Station loses connection
- **WHEN** the station tablet cannot reach the system for more than 10 seconds
- **THEN** the station shows a connection alert with a sound

#### Scenario: Alert ends
- **WHEN** the printer is ready again and all waiting tickets have printed
- **THEN** the alert disappears without staff action

### Requirement: Resolve unclear tickets
Unclear tickets SHALL be shown at the top of the station list with their ticket number until resolved. Station staff SHALL resolve each one either as "was printed", which sets it to printed without printing again, or as "reprint", which prints the same content marked "REPRINT" and resolves the original. The resolution SHALL record when it happened and at which station.

#### Scenario: Ticket was in the pile
- **WHEN** ticket "BAR 0142" is unclear and the staff find it in the pile and choose "was printed"
- **THEN** the ticket becomes printed, nothing is printed again, and the alert for it ends

#### Scenario: Ticket is missing
- **WHEN** ticket "BAR 0142" is unclear, the staff cannot find it, and they choose "reprint"
- **THEN** the bar printer prints the ticket marked "REPRINT" with the number "BAR 0142", and the original is resolved

#### Scenario: Resolved twice
- **WHEN** two stations or two taps resolve the same unclear ticket
- **THEN** only the first resolution takes effect and at most one reprint is printed

### Requirement: Reprint from the station
Station staff SHALL be able to reprint any ticket of their assigned printers that is not waiting or being printed. The reprint SHALL follow the existing reprint rules.

#### Scenario: Kitchen asks for a ticket again
- **WHEN** the kitchen station reprints a printed food ticket
- **THEN** the kitchen printer prints it again marked "REPRINT"

### Requirement: Station stays usable for the whole event
The station screen SHALL keep the display awake while it is open, and SHALL ask for one tap after login to enable sound alerts.

#### Scenario: Idle station
- **WHEN** no ticket arrives for 30 minutes
- **THEN** the station screen is still on and shows the ticket list and printer status
