# Spec Delta

## Purpose

Keeps the salon's customer records so that services can be sold to and invoiced for a known customer, and reports can show revenue per customer, while protecting the customers' personal data.

## ADDED Requirements

### Requirement: Customer records
In salon mode, staff and admins SHALL create and edit customers. Each customer has a first name and a last name (at least one required, each at most 60 characters), an optional phone (at most 30 characters), an optional email, an optional address (street, postal code, city), an optional note of at most 500 characters, and an active flag.

#### Scenario: Create a customer with name only
- **WHEN** a staff member creates the customer "Laura Meier" without further details
- **THEN** the customer is stored as active and can be picked for an order right away

#### Scenario: Invalid email rejected
- **WHEN** a staff member saves a customer with the email "laura@"
- **THEN** the system rejects it with a validation error

#### Scenario: No name rejected
- **WHEN** a staff member saves a customer without first and last name
- **THEN** the system rejects it with a validation error

### Requirement: Customer number
Each customer SHALL get a unique, running customer number when created. The number SHALL never change and SHALL never be given to another customer.

#### Scenario: Number on creation
- **WHEN** a customer is created after customer number 41
- **THEN** the new customer gets number 42

### Requirement: Find customers
Staff and admins SHALL search customers by name, customer number, phone, or email, ignoring upper and lower case. Results SHALL list active customers by last name and first name, with phone and email. Admins SHALL be able to include inactive customers.

#### Scenario: Search by part of a name
- **WHEN** a staff member searches for "mei"
- **THEN** active customers such as "Laura Meier" and "Peter Meili" are listed

#### Scenario: Search by phone
- **WHEN** a staff member searches for "079 555"
- **THEN** customers whose phone contains these digits are listed, regardless of spaces

### Requirement: Possible duplicate warning
When a customer is created whose first and last name, phone, or email matches an existing customer, the system SHALL warn the user and list the matches. The user SHALL be able to create the customer anyway.

#### Scenario: Same name exists
- **WHEN** a staff member creates "Laura Meier" and an active "Laura Meier" exists
- **THEN** the app shows the existing customer and asks whether to use it or create a new one

### Requirement: Deactivate customers
Admins SHALL be able to deactivate and reactivate customers. Inactive customers SHALL NOT be offered when building an order, but their orders, invoices, and report data SHALL stay unchanged.

#### Scenario: Deactivate a customer
- **WHEN** an admin deactivates "Peter Meili"
- **THEN** staff can no longer pick this customer for a new order, and the past invoices still show the customer name

### Requirement: Customer history
Staff and admins SHALL see, for each customer, the list of their invoices with invoice number, date, total, and payment status, newest first, and the customer's total revenue.

#### Scenario: Open a customer
- **WHEN** a staff member opens "Laura Meier", who has 3 invoices
- **THEN** the 3 invoices are listed newest first with number, date, total, and status, and the sum of the not cancelled invoices is shown

### Requirement: Delete or anonymize on request
Admins SHALL be able to delete a customer. A customer without orders SHALL be deleted completely. A customer with orders SHALL be anonymized: name, contact details, address, and note are removed from the customer record, and the record stays for the reports. Issued invoices SHALL keep their customer details for the legal retention period.

#### Scenario: Delete a customer without orders
- **WHEN** an admin deletes a customer that was never used in an order
- **THEN** the customer is removed and no longer found

#### Scenario: Anonymize a customer with invoices
- **WHEN** an admin deletes "Laura Meier", who has invoices
- **THEN** the customer record shows "Deleted customer" with the customer number and no contact data, and the issued invoices still show the details they were issued with

#### Scenario: Staff cannot delete
- **WHEN** a staff member tries to delete a customer
- **THEN** the system rejects the request as forbidden

### Requirement: Customer data export
Admins SHALL be able to export all data stored about one customer, including the customer record and their invoices, as a file, to answer an access request.

#### Scenario: Access request
- **WHEN** an admin exports the data of "Laura Meier"
- **THEN** a file is created with the customer record, the invoices with lines, and their payment status
