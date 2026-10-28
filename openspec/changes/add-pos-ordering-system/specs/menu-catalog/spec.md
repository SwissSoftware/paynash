# Spec Delta

## Purpose

Defines the food and drink items (menu items) that waiters can order. It covers item type, category, price, availability, display order, and an optional image that makes items easy to recognize in the order screen.

## ADDED Requirements

### Requirement: Admin manages menu items
Admins SHALL create and edit menu items. Each item has a name (required, at most 80 characters), a type (`food` or `drink`), a category, a price in CHF with exactly two decimals and a value of at least 0.00, an optional short description, a display order, and an active flag.

#### Scenario: Create a drink item
- **WHEN** an admin creates an item "Espresso" of type `drink` in category "Hot drinks" with price 4.50
- **THEN** the item is stored and appears in the waiter app under "Hot drinks"

#### Scenario: Invalid price rejected
- **WHEN** an admin saves an item with a negative price or more than two decimals
- **THEN** the system rejects it with a validation error

#### Scenario: Missing type rejected
- **WHEN** an admin saves an item without a type
- **THEN** the system rejects it, because the type is required for printer routing

### Requirement: Categories
Admins SHALL create, rename, reorder, and delete categories. A category SHALL NOT be deleted while it still contains items.

#### Scenario: Delete non-empty category
- **WHEN** an admin deletes a category that still contains items
- **THEN** the system rejects the deletion and names the remaining items

### Requirement: Deactivate instead of delete
Admins SHALL deactivate items instead of deleting them, so past orders stay intact. Deactivated items SHALL NOT be offered in the waiter app, but SHALL remain visible in existing orders and exports. Admins SHALL be able to reactivate them.

#### Scenario: Item sold out for the day
- **WHEN** an admin deactivates "Tiramisu"
- **THEN** waiters can no longer add it to orders, and existing orders that contain it are unchanged

### Requirement: Price snapshot
A change to an item's name or price SHALL NOT change existing order lines. Each order line SHALL keep the name and unit price that were valid when it was added.

#### Scenario: Price change after ordering
- **WHEN** an item's price changes from 4.50 to 5.00 after it was ordered
- **THEN** the existing order line still shows 4.50, and new lines use 5.00

### Requirement: Item images
Admins SHALL be able to upload, replace, and remove one image per item. Accepted formats are JPEG, PNG, and WebP up to 5 MB. The system SHALL store a resized version for display, and the waiter app SHALL show it as the item's tile.

#### Scenario: Upload image
- **WHEN** an admin uploads a 2 MB JPEG for "Margherita"
- **THEN** the image is stored, and the waiter app shows it on the "Margherita" tile

#### Scenario: Unsupported file rejected
- **WHEN** an admin uploads a PDF or a file larger than 5 MB
- **THEN** the system rejects it with a clear error, and the existing image stays unchanged

#### Scenario: Item without image
- **WHEN** an item has no image
- **THEN** the waiter app shows a placeholder tile with the item name and a food or drink icon

### Requirement: Waiter menu view
The waiter app SHALL show active items grouped by category, ordered by display order, with image, name, and price. Food and drink items SHALL be visually distinguishable. Users SHALL be able to filter by category.

#### Scenario: Browse by category
- **WHEN** a waiter selects the category "Pizzas"
- **THEN** only the active items in "Pizzas" are shown as image tiles with name and price
