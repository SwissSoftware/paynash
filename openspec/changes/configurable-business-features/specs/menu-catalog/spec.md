# Spec Delta

## MODIFIED Requirements

### Requirement: Admin manages menu items
Admins SHALL create and edit menu items. Each item has a name (required, at most 80 characters), a type (`food`, `drink`, or `service`), a category, a price in CHF with exactly two decimals and a value of at least 0.00, a VAT rate, an optional short description, a display order, and an active flag. Items of type `service` MAY also have a duration in minutes. Items of all types MAY be mixed in one menu, independent of the enabled features.

#### Scenario: Create a drink item
- **WHEN** an admin creates an item "Espresso" of type `drink` in category "Hot drinks" with price 4.50
- **THEN** the item is stored and appears in the waiter app under "Hot drinks"

#### Scenario: Invalid price rejected
- **WHEN** an admin saves an item with a negative price or more than two decimals
- **THEN** the system rejects it with a validation error

#### Scenario: Missing type rejected
- **WHEN** an admin saves an item without a type
- **THEN** the system rejects it, because the type is required for printer routing

#### Scenario: Create a service
- **WHEN** an admin creates the service "Facial" in category "Face" with price 120.00 and a duration of 60 minutes
- **THEN** the service is stored and appears in the order screen under "Face"

#### Scenario: Hair salon with drinks
- **WHEN** an admin creates the service "Haircut" in category "Hair" and the item "Espresso" of type `drink` in category "Drinks"
- **THEN** both are stored, and both appear in the order screen

## REMOVED Requirements

### Requirement: Item types per mode
**Reason**: Item types no longer depend on a mode. A business may sell services, food, and drinks side by side.
**Migration**: See "Admin manages menu items" in this capability. Whether a line prints a production ticket follows the printer's category assignment (`ticket-printing`).
