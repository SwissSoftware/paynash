# Spec Delta

## MODIFIED Requirements

### Requirement: Create and send an order
A logged-in waiter SHALL be able to build an order for a table and send it in one step. The waiter SHALL pick the table from the list of active tables or, as a fallback, enter a table label of 1 to 10 alphanumeric characters. A picked table SHALL use the table's number as the order's table label. Sending SHALL save the order with status `sent`, the table label, the sending waiter, and the time sent, and SHALL hand all its lines to ticket printing. An order with no lines SHALL NOT be sent. A table MAY have any number of sent orders.

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
- **WHEN** no active tables exist
- **THEN** the waiter sees an empty table list with the manual entry field and can send an order by typing a label
