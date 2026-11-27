# Spec Delta

## MODIFIED Requirements

### Requirement: Report access
Only admins SHALL be able to see the report and export it. The report SHALL be available only while `sales-report` is on.

#### Scenario: Staff cannot see the report
- **WHEN** a staff member requests the report or the Excel export
- **THEN** the system rejects the request as forbidden

#### Scenario: Report switched off
- **WHEN** an admin requests the report while `sales-report` is off
- **THEN** the system rejects the request with a message that the sales report is switched off
