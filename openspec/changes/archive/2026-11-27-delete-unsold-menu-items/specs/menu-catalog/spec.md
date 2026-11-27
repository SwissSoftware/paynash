# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Deactivate instead of delete`
- TO: `### Requirement: Delete unused items, deactivate ordered ones`

## MODIFIED Requirements

### Requirement: Delete unused items, deactivate ordered ones
Admins SHALL be able to delete an item of any type that has never been ordered. An item that appears in any order line, including cancelled lines and lines of voided orders, SHALL NOT be deleted; admins SHALL deactivate it instead, so past orders stay intact. Deactivated items SHALL NOT be offered in the waiter app, but SHALL remain visible in existing orders and exports. Admins SHALL be able to reactivate them.

#### Scenario: Item sold out for the day
- **WHEN** an admin deactivates "Tiramisu"
- **THEN** waiters can no longer add it to orders, and existing orders that contain it are unchanged

#### Scenario: Delete an item that was never ordered
- **WHEN** an admin deletes the drink "Espreso", which was created by mistake and never ordered
- **THEN** the item and its image are removed, and it no longer appears in the admin list or the waiter app

#### Scenario: Delete a service that was never ordered
- **WHEN** an admin deletes the service "Test service", which was never ordered
- **THEN** the service is removed, and it no longer appears in the admin list or the order screen

#### Scenario: Delete an ordered item rejected
- **WHEN** an admin deletes "Margherita", which appears in at least one order line
- **THEN** the system rejects the deletion, tells the admin that the item was already ordered and can be deactivated instead, and the item and all orders that contain it stay unchanged

#### Scenario: Item ordered only in a voided order
- **WHEN** an admin deletes an item whose only order line belongs to a voided order
- **THEN** the system rejects the deletion, because the voided order still references the item

#### Scenario: Category empty after deletion
- **WHEN** an admin deletes the only item of the category "Test", and that item was never ordered
- **THEN** the category "Test" is empty and can be deleted
