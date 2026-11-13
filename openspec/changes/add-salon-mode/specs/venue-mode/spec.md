# Spec Delta

## Purpose

Lets one installation run either as an event/restaurant or as a beauty salon, and holds the business details that salon invoices need, so the same system serves both target groups without mixing their data.

## ADDED Requirements

### Requirement: Installation mode
Each installation SHALL run in exactly one mode: `event` or `salon`. The mode SHALL be `event` unless an admin changes it. All staff SHALL see the current mode, and the apps SHALL adapt their functions and terms to it.

#### Scenario: Existing installation after the update
- **WHEN** an installation with event data is updated to this version
- **THEN** it runs in `event` mode and behaves as before

#### Scenario: Admin sets up a salon
- **WHEN** an admin of a new installation without orders sets the mode to `salon`
- **THEN** the mode is stored, and every app shows the salon functions after its next refresh

### Requirement: Mode change only without orders
An admin SHALL be able to change the mode only while no order exists. Once an order exists, a mode change SHALL be rejected, so event and salon data never mix in one installation.

#### Scenario: Change rejected after first order
- **WHEN** an admin tries to change the mode from `event` to `salon` and orders exist
- **THEN** the system rejects the change with a message that the installation already has orders

#### Scenario: Waiter cannot change the mode
- **WHEN** a waiter tries to change the mode
- **THEN** the system rejects the request as forbidden

### Requirement: Functions per mode
In `salon` mode the apps SHALL show customers, services, invoices, and the sales report, and SHALL hide tables and the station view. In `event` mode the apps SHALL hide customers, invoices, and the sales report. Requests for a function that the current mode does not offer SHALL be rejected.

#### Scenario: Salon hides tables
- **WHEN** a staff member opens the order screen in `salon` mode
- **THEN** it asks for a customer, and no table list or table administration is shown

#### Scenario: Invoice request in event mode
- **WHEN** a request to issue an invoice reaches an installation in `event` mode
- **THEN** the system rejects it with a message that invoicing is only available in salon mode

### Requirement: Salon terms
In `salon` mode the apps SHALL call the `waiter` role "staff", menu items "services", and sending an order "confirming" it. The stored roles and states SHALL stay the same in both modes.

#### Scenario: Staff list in a salon
- **WHEN** an admin opens the staff administration in `salon` mode
- **THEN** accounts with the role `waiter` are shown as "staff"

### Requirement: Business details
Admins SHALL maintain the business details: business name (required, at most 80 characters), street, postal code, and city (all required in salon mode), and optional phone, email, website, and bank details (IBAN and account holder). The IBAN SHALL be a valid Swiss or Liechtenstein IBAN.

#### Scenario: Save business details
- **WHEN** an admin saves the name "Kosmetik Aurora", address "Bahnhofstrasse 1, 3000 Bern", and phone "031 123 45 67"
- **THEN** the details are stored and used on new invoices

#### Scenario: Invalid IBAN rejected
- **WHEN** an admin saves the IBAN "CH00 1234"
- **THEN** the system rejects it with a validation error

### Requirement: VAT setting
Admins SHALL be able to turn VAT on or off. Turning it on SHALL require a VAT number (UID) in the format "CHE-123.456.789 MWST" with a valid check digit. The setting SHALL apply only to invoices issued after the change.

#### Scenario: Turn VAT on
- **WHEN** an admin turns VAT on with the UID "CHE-116.281.710 MWST"
- **THEN** the setting is stored, and invoices issued from now on show the VAT breakdown

#### Scenario: VAT without UID rejected
- **WHEN** an admin turns VAT on without a UID or with an invalid UID
- **THEN** the system rejects it with a validation error, and VAT stays off

#### Scenario: Existing invoices unchanged
- **WHEN** VAT is turned on after invoice "2026-0012" was issued without VAT
- **THEN** invoice "2026-0012" is still shown, printed, and exported without VAT

### Requirement: Invoicing needs business details
In `salon` mode the system SHALL reject issuing an invoice while the required business details are missing, and SHALL tell the user which details are missing.

#### Scenario: Missing address
- **WHEN** staff try to issue an invoice and the business address is not set
- **THEN** the system rejects it with "business address missing, ask an admin to complete the settings"
