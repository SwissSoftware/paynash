# Spec Delta

## MODIFIED Requirements

### Requirement: Order lines with food and drinks
An order SHALL contain any mix of food and drink lines. Each line references an active menu item and has a quantity from 1 to 99, an optional note of at most 140 characters (e.g. "no onions"), and the item's name, type, and unit price at the time it was sent. Before sending, lines can be added, changed, and removed freely.

#### Scenario: Add mixed items
- **WHEN** a waiter adds 2 × "Cola" and 1 × "Margherita" with the note "extra spicy"
- **THEN** the draft order shows both lines, with the note on the pizza and a correct running total

#### Scenario: Inactive item rejected
- **WHEN** an order is sent that contains an item which was deactivated in the meantime
- **THEN** the system rejects the whole order and names the inactive items, nothing is printed, and the app refreshes the menu and keeps the other lines in the draft

#### Scenario: Deleted item rejected
- **WHEN** an order is sent that contains an item which was deleted in the meantime
- **THEN** the system rejects the whole order as for an inactive item, nothing is saved or printed, and the app removes the deleted item's lines, refreshes the menu, and keeps the other lines in the draft
