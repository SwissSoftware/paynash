# Spec Delta

## MODIFIED Requirements

### Requirement: Customer records
While `customers` is on, staff and admins SHALL create and edit customers. Each customer has a first name and a last name (at least one required, each at most 60 characters), an optional phone (at most 30 characters), an optional email, an optional address (street, postal code, city), an optional note of at most 500 characters, and an active flag.

#### Scenario: Create a customer with name only
- **WHEN** a staff member creates the customer "Laura Meier" without further details
- **THEN** the customer is stored as active and can be picked for an order right away

#### Scenario: Invalid email rejected
- **WHEN** a staff member saves a customer with the email "laura@"
- **THEN** the system rejects it with a validation error

#### Scenario: No name rejected
- **WHEN** a staff member saves a customer without first and last name
- **THEN** the system rejects it with a validation error
