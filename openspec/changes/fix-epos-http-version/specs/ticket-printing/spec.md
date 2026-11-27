# Spec Delta

## ADDED Requirements

### Requirement: Printer-compatible connection
The system SHALL send ePOS-Print requests to printers as plain HTTP/1.1 and SHALL NOT ask the printer to switch protocols (for example an `Upgrade: h2c` request). Printers whose embedded web server only speaks HTTP/1.x SHALL receive and print tickets, receipts, and test prints.

#### Scenario: Test print on an Epson printer
- **WHEN** an admin triggers a test print for a reachable Epson printer with ePOS-Print enabled and the correct device ID
- **THEN** the printer prints the test ticket and the admin sees that it succeeded

#### Scenario: Printer that refuses protocol upgrades
- **WHEN** a printer closes the connection without answer whenever a request asks for a protocol upgrade
- **THEN** tickets sent to it are still printed and marked `printed`

#### Scenario: No upgrade header
- **WHEN** the system sends any ePOS-Print request
- **THEN** the request uses HTTP/1.1 and carries no `Upgrade` header
