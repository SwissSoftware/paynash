# Spec Delta

## MODIFIED Requirements

### Requirement: Admin manages menu items
Admins SHALL create and edit menu items. Each item has a name (required, at most 80 characters), a type (`food`, `drink`, or `service`), a category, a price in CHF with exactly two decimals and a value of at least 0.00, a VAT rate, an optional short description, a display order, and an active flag. Items of type `service` MAY also have a duration in minutes.

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
- **WHEN** an admin in salon mode creates the service "Facial" in category "Face" with price 120.00 and a duration of 60 minutes
- **THEN** the service is stored and appears in the order screen under "Face"

## ADDED Requirements

### Requirement: Item types per mode
In salon mode, admins SHALL only create items of type `service`, and only services SHALL be offered when building an order. In event mode, admins SHALL NOT create items of type `service`.

#### Scenario: Food item in a salon rejected
- **WHEN** an admin in salon mode saves an item of type `food`
- **THEN** the system rejects it with a validation error

#### Scenario: Service in event mode rejected
- **WHEN** an admin in event mode saves an item of type `service`
- **THEN** the system rejects it with a validation error

### Requirement: Service duration
The duration of a service SHALL be a whole number of minutes from 5 to 600. It SHALL be shown on the service tile and SHALL NOT affect prices or invoices.

#### Scenario: Invalid duration rejected
- **WHEN** an admin saves a service with a duration of 0 or 900 minutes
- **THEN** the system rejects it with a validation error

#### Scenario: Duration on the tile
- **WHEN** staff open the order screen and "Facial" has a duration of 60 minutes
- **THEN** the tile shows "Facial", "120.00", and "60 min"

### Requirement: VAT rate per item
Each item SHALL have a VAT rate of 8.1 %, 2.6 %, 3.8 %, or 0 %. New items SHALL get 8.1 % unless the admin chooses another rate. The VAT rate SHALL be shown and editable only while VAT is turned on. Each order line SHALL keep the VAT rate that was valid when the line was added.

#### Scenario: Default rate
- **WHEN** an admin with VAT turned on creates a service without choosing a rate
- **THEN** the service gets the VAT rate 8.1 %

#### Scenario: Rate change after selling
- **WHEN** a service's VAT rate changes from 8.1 % to 0 % after it was sold
- **THEN** the existing order line and its invoice keep 8.1 %, and new lines use 0 %

#### Scenario: Invalid rate rejected
- **WHEN** an admin saves an item with the VAT rate 7.7 %
- **THEN** the system rejects it with a validation error

### Requirement: Service placeholder tile
A service without an image SHALL be shown as a placeholder tile with its name and a service icon.

#### Scenario: Service without image
- **WHEN** the service "Manicure" has no image
- **THEN** the order screen shows a placeholder tile with "Manicure" and a service icon
