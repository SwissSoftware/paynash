# business-features Specification

## Purpose
Makes one Paynash installation fit different kinds of businesses (restaurant, event, salon, counter sales, and mixes of them) by switching individual functions on and off, starting from business templates, and naming key terms to fit the business.

## Requirements

### Requirement: Feature set
Each installation SHALL have a set of features that are each switched on or off: `tables` (tables and table administration), `stations` (station accounts and the station view), `customers` (customer management), `invoices` (invoices, receipts, and invoice PDFs), and `sales-report` (sales report and Excel export). Only admins SHALL change the features. All logged-in users SHALL see which features are on.

#### Scenario: Admin switches on invoices
- **WHEN** an admin of a restaurant with `tables` and `customers` on switches on `invoices`
- **THEN** the feature is stored, and every app shows the invoice functions after its next refresh

#### Scenario: Waiter cannot change features
- **WHEN** a waiter tries to switch a feature on or off
- **THEN** the system rejects the request as forbidden

### Requirement: Business templates
Admins SHALL be able to apply a business template, which sets the features and the terms in one step: Event (`tables`, `stations`), Restaurant (`tables`, `stations`, `customers`, `invoices`, `sales-report`), Salon (`customers`, `invoices`, `sales-report`, salon terms), and Counter sales (no features). Applying a template SHALL NOT change any other data.

#### Scenario: Set up a café
- **WHEN** an admin applies the template Counter sales
- **THEN** all features are off, and staff sell items without picking a table or customer

#### Scenario: Adjust a template
- **WHEN** an admin applies Salon and then switches on `tables`
- **THEN** both changes are kept, and the installation shows the template as Custom

### Requirement: Switching features with existing data
Switching a feature on SHALL always be possible. Switching a feature off SHALL hide its functions and reject new requests for them, but SHALL keep all existing data, so history, invoices, and reports stay complete. Switching the feature on again SHALL show the kept data again.

#### Scenario: Tables off after the summer
- **WHEN** an admin switches off `tables` while orders with table labels exist
- **THEN** the order history still shows those table labels, and new orders are sent without a table

#### Scenario: Invoices off and on again
- **WHEN** an admin switches off `invoices` and later on again
- **THEN** all invoices issued before are listed again with unchanged numbers, and numbering continues without a gap

### Requirement: Functions follow the features
The apps SHALL show only the functions of enabled features, and the system SHALL reject requests for a function whose feature is off with a message that names the feature. Functions without a feature (menu, orders, printers, staff, settings, exports) SHALL always be available.

#### Scenario: Invoice request with invoices off
- **WHEN** a request to issue an invoice reaches an installation with `invoices` off
- **THEN** the system rejects it with a message that invoices are switched off

#### Scenario: Station view with stations off
- **WHEN** a staff member opens the app in an installation with `stations` off
- **THEN** no station functions are shown

### Requirement: Configurable terms
Admins SHALL be able to name three terms: the `waiter` role (e.g. "Waiter", "Staff"), menu items (e.g. "Items", "Services"), and sending an order (e.g. "Send", "Confirm"), each 1 to 30 characters. Templates SHALL set default terms. The apps SHALL use the terms, while stored roles and states stay the same.

#### Scenario: Salon terms
- **WHEN** the terms are "Staff", "Services", and "Confirm"
- **THEN** the staff administration shows accounts with the role `waiter` as "Staff", and the order screen's button reads "Confirm"

#### Scenario: Empty term rejected
- **WHEN** an admin saves an empty name for menu items
- **THEN** the system rejects it with a validation error

### Requirement: Existing installations keep their behaviour
When an installation is updated to this version, the system SHALL convert its mode: `event` into the template Event with its features and default terms, and `salon` into the template Salon with its features and the salon terms. Data SHALL stay unchanged.

#### Scenario: Event installation after the update
- **WHEN** an installation in `event` mode with tables and orders is updated
- **THEN** `tables` and `stations` are on, all other features are off, and the apps behave as before

#### Scenario: Salon installation after the update
- **WHEN** an installation in `salon` mode with customers and invoices is updated
- **THEN** `customers`, `invoices`, and `sales-report` are on, the terms are "Staff", "Services", and "Confirm", and the apps behave as before

### Requirement: Sales report needs invoices
`sales-report` SHALL require `invoices`, because the report is based on invoices. `invoices` SHALL NOT require any other feature. The system SHALL reject a feature set with `sales-report` but without `invoices` and name the missing or dependent feature.

#### Scenario: Report without invoices rejected
- **WHEN** an admin switches on `sales-report` while `invoices` is off
- **THEN** the system rejects the change with a message that `sales-report` needs `invoices`

#### Scenario: Invoices still needed by the report
- **WHEN** an admin switches off `invoices` while `sales-report` is on
- **THEN** the system rejects the change with a message that `sales-report` must be switched off first

#### Scenario: Invoices without customers
- **WHEN** an admin of a café with all features off switches on `invoices` and `sales-report`
- **THEN** the change is stored, and `customers` stays off
