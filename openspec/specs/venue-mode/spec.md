# venue-mode Specification

## Purpose
Lets one installation run either as an event/restaurant or as a beauty salon, and holds the business details that salon invoices need, so the same system serves both target groups without mixing their data.

## Requirements

### Requirement: Business details
Admins SHALL maintain the business details: business name (required, at most 80 characters), street, postal code, and city (all required while `invoices` is on), and optional phone, email, website, and bank details (IBAN and account holder). The IBAN SHALL be a valid Swiss or Liechtenstein IBAN.

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
While `invoices` is on, the system SHALL reject issuing an invoice while the required business details are missing, and SHALL tell the user which details are missing.

#### Scenario: Missing address
- **WHEN** staff try to issue an invoice and the business address is not set
- **THEN** the system rejects it with "business address missing, ask an admin to complete the settings"
