# Spec Delta

## Purpose

Lets the admin keep a list of the restaurant's real tables, each with a number and an optional name, so that waiters can pick a table when taking an order instead of typing it.

## ADDED Requirements

### Requirement: Admin manages tables
An admin SHALL be able to create and edit tables. Each table SHALL have a number of 1 to 10 letters or digits and an optional name of at most 60 characters. Numbers SHALL be stored upper-case, in the same form as order table labels. Changing a table's number or name SHALL NOT change orders already sent.

#### Scenario: Create a table with a name
- **WHEN** an admin creates a table with number "12" and name "Terrace window"
- **THEN** the table is saved as active and appears in the admin table list with number "12" and name "Terrace window"

#### Scenario: Create a table without a name
- **WHEN** an admin creates a table with number "t5" and no name
- **THEN** the table is saved with number "T5" and no name

#### Scenario: Invalid number rejected
- **WHEN** an admin saves a table with an empty number, a number longer than 10 characters, or a number containing characters other than letters and digits
- **THEN** the system rejects it with a validation error on the number field

#### Scenario: Rename a table
- **WHEN** an admin changes the name of table "12" from "Terrace window" to "Terrace corner"
- **THEN** the admin list and the waiter list both show the new name

#### Scenario: Renumbering keeps sent orders
- **WHEN** orders were sent for table "12" and an admin changes the table's number to "14"
- **THEN** the waiter list shows "14", and the orders already sent still show table "12" in the order history and the CSV export

### Requirement: Unique table numbers
Table numbers SHALL be unique, ignoring case. Deactivated tables also count.

#### Scenario: Duplicate number rejected
- **WHEN** table "12" exists and an admin creates or renumbers another table to "12"
- **THEN** the system rejects it with a validation error saying the number is already in use

#### Scenario: Duplicate number with different case rejected
- **WHEN** table "T5" exists and an admin creates a table with number "t5"
- **THEN** the system rejects it as a duplicate

### Requirement: Deactivate and reactivate tables
An admin SHALL be able to deactivate a table and reactivate it later. Deactivated tables SHALL NOT appear in the waiter's table list but SHALL stay in the admin list, marked as inactive.

#### Scenario: Deactivated table hidden from waiters
- **WHEN** an admin deactivates table "T1"
- **THEN** "T1" no longer appears in the waiter's table list and is shown as inactive in the admin list

#### Scenario: Reactivate a table
- **WHEN** an admin reactivates table "T1"
- **THEN** "T1" appears in the waiter's table list again

### Requirement: Delete tables
An admin SHALL be able to delete a table permanently. Deleting a table SHALL NOT change orders already sent, their tickets, or the CSV export, which keep the table number they were sent with.

#### Scenario: Delete a table
- **WHEN** an admin deletes table "8"
- **THEN** the table disappears from the admin and waiter lists, and its number can be used for a new table

#### Scenario: Past orders keep their table
- **WHEN** orders were sent for table "8" and an admin deletes it
- **THEN** those orders still show table "8" in the order history and the CSV export

### Requirement: Waiter table list
A logged-in waiter or admin SHALL be able to fetch the list of active tables, sorted by number with numbers compared numerically where possible ("2" before "10"). Each entry SHALL show the number and the name if set.

#### Scenario: List shows active tables in order
- **WHEN** active tables "10", "2", and "T1" exist and table "3" is inactive
- **THEN** the waiter sees "2", "10", "T1" in that order, and "3" is not listed

### Requirement: Table administration access
Only admins SHALL be able to create, edit, deactivate, reactivate, or delete tables. Waiters SHALL only be able to read the active table list.

#### Scenario: Waiter cannot change tables
- **WHEN** a waiter tries to create, edit, or delete a table
- **THEN** the system rejects the request with 403 Forbidden and nothing changes

#### Scenario: Not logged in
- **WHEN** a request for the table list has no valid session
- **THEN** the system rejects it with 401 Unauthorized
