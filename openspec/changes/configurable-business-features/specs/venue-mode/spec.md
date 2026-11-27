# Spec Delta

## MODIFIED Requirements

### Requirement: Business details
Admins SHALL maintain the business details: business name (required, at most 80 characters), street, postal code, and city (all required while `invoices` is on), and optional phone, email, website, and bank details (IBAN and account holder). The IBAN SHALL be a valid Swiss or Liechtenstein IBAN.

#### Scenario: Save business details
- **WHEN** an admin saves the name "Kosmetik Aurora", address "Bahnhofstrasse 1, 3000 Bern", and phone "031 123 45 67"
- **THEN** the details are stored and used on new invoices

#### Scenario: Invalid IBAN rejected
- **WHEN** an admin saves the IBAN "CH00 1234"
- **THEN** the system rejects it with a validation error

### Requirement: Invoicing needs business details
While `invoices` is on, the system SHALL reject issuing an invoice while the required business details are missing, and SHALL tell the user which details are missing.

#### Scenario: Missing address
- **WHEN** staff try to issue an invoice and the business address is not set
- **THEN** the system rejects it with "business address missing, ask an admin to complete the settings"

## REMOVED Requirements

### Requirement: Installation mode
**Reason**: The fixed modes `event` and `salon` are replaced by individually switchable features and business templates.
**Migration**: See `business-features`, "Feature set", "Business templates", and "Existing installations keep their behaviour". `event` becomes the template Event, `salon` the template Salon.

### Requirement: Mode change only without orders
**Reason**: Features can be switched at any time because switching a feature off keeps its data.
**Migration**: See `business-features`, "Switching features with existing data".

### Requirement: Functions per mode
**Reason**: Which functions exist now depends on the enabled features, not on a mode.
**Migration**: See `business-features`, "Functions follow the features".

### Requirement: Salon terms
**Reason**: Terms are configurable for every business, with defaults from the template.
**Migration**: See `business-features`, "Configurable terms". The template Salon sets "Staff", "Services", and "Confirm".
