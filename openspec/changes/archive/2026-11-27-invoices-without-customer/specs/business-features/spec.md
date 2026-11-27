# Spec Delta

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Feature dependencies
**Reason**: Invoices no longer need a customer (spec invoicing, "Issue an invoice"), so `invoices` no longer requires `customers`. The remaining dependency is described on its own.
**Migration**: See "Sales report needs invoices" in this capability. Migration V17 drops the database check that tied invoices to customers.
