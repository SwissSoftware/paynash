# Proposal

## Why

Paynash is built for restaurants and larger events: orders belong to a table, lines are food or drinks, and sent orders print kitchen and bar tickets. Small beauty salons (Kosmetiksalons) are a second target group with different needs. They sell **services** (e.g. facial, manicure) to **known customers**, have no tables or kitchen, must give the customer an **invoice**, and at the end of each month need a report of what was sold, to whom, and how much revenue each day brought. Today the system has no customers, no services, no invoices, and only a raw CSV order export, so a salon cannot use it. This change makes Paynash usable for a salon without breaking the event setup.

## What Changes

- New **salon mode** for an installation. An admin chooses whether an installation runs as an *event/restaurant* (current behavior, the default) or as a *salon*. In salon mode the UI uses salon terms and hides table functions. Printers stay available.
- New **business settings**: salon name, address, contact details, optional bank details, and whether VAT is shown. If VAT is shown, the VAT number (UID) and a VAT rate per service are required.
- New **customer management**: staff can create, search, edit, and deactivate customers (name, phone, email, address, optional notes). A customer with invoices is never hard-deleted, so invoices and reports stay complete.
- New item type **service** in the catalog, with name, category, price, optional duration, and (if VAT is shown) a VAT rate. Services are managed like menu items.
- **Recording a service sale**: in salon mode, staff build an order for a **customer instead of a table** and confirm it. No kitchen or bar tickets are printed. Lines can be cancelled and orders voided before an invoice is issued.
- New **invoicing**: from a confirmed order, staff issue an **invoice** with a unique, gapless invoice number, the business and customer details, the lines, the total, and (if enabled) the VAT breakdown. An issued invoice cannot be changed. A mistake is corrected by cancelling the invoice (the number stays used and is marked cancelled) and issuing a new one.
- **Two invoice outputs**: a **receipt on the Epson receipt printer** right after the treatment, and an **A4 invoice as PDF** to download, print, or send to the customer. Both can be produced again at any time and show the same content.
- **Payment status**: each invoice is *open* or *paid*. When marking it paid, staff choose the payment method (cash, card, TWINT, bank transfer) and the payment date. There is no connection to a card terminal and no QR bill.
- New **sales report** on the devices (web, Android, iOS): for a chosen month (default the current month) or date range, it shows revenue per day, per service (quantity and amount), and per customer, the total, paid and open amounts, revenue per payment method, and (if VAT is shown) VAT per rate. Cancelled invoices, cancelled lines, and voided orders are excluded.
- **Excel export (.xlsx)** of the report, with sheets for daily revenue, services, customers, invoices (with payment status), and the individual lines.
- **Revenue** means the total of the issued, not cancelled invoices in the period, by invoice date.
- **Out of scope**: appointment booking and calendar (Terminverwaltung), assigning services to staff members, card terminals, Swiss QR bills, sending invoices by email from the app, payment reminders (Mahnungen), accounting exports, product (retail) stock, customer self-service, and multi-location or multi-tenant operation on one installation.

## Capabilities

### New Capabilities
- `venue-mode`: Choosing whether an installation runs as an event/restaurant or as a salon, which functions and terms each mode shows, and the business settings (company details, VAT on/off, UID).
- `customer-management`: Customer records (create, search, edit, deactivate), required fields and validation, data protection rules, and who may manage customers.
- `invoicing`: Issuing invoices from orders, invoice numbering, invoice content and VAT breakdown, cancelling invoices, the printed receipt and the A4 PDF, and the payment status with payment method.
- `sales-reporting`: The period sales report on the devices and its Excel (.xlsx) export, including what counts as revenue and access control.

### Modified Capabilities
- `menu-catalog`: Adds the item type `service` with an optional duration and a VAT rate per item.
- `order-taking`: In salon mode, an order belongs to a customer instead of a table, is confirmed instead of sent to production, and cannot be changed once it is invoiced. The order history shows the customer and the invoice status.
- `ticket-printing`: Service lines are not routed to production printers, and invoice receipts are a new print job type on a configured receipt printer, with the same delivery guarantees as tickets.
- `staff-access`: Role permissions cover the salon functions. Waiters (shown as "staff" in salon mode) manage customers, issue invoices, print receipts, and mark invoices paid. Only admins cancel invoices, change settings, and see the sales report and its export.

<!-- menu-catalog, order-taking, ticket-printing and staff-access are defined by the in-flight changes
     add-pos-ordering-system, add-table-management and harden-printing-for-events,
     which are not archived yet. The delta specs here build on their requirements. -->

## Impact

- **Backend**: new modules for settings, customers, invoicing (numbering, PDF generation), and reporting (aggregation queries, .xlsx generation). The menu module gets the `service` type, duration, and VAT rate. Orders get an optional customer (table becomes optional in salon mode). Printing gets a receipt job type and a receipt layout for the Epson TM-m30III.
- **App (Flutter, web/Android/iOS)**: settings screen, customer list and editor, customer picker in the order flow, invoice view with "print receipt", "download PDF", "mark as paid", and "cancel invoice", the report screen, and an Excel download.
- **Data**: new tables for settings, customers, invoices, and payments. New columns on menu items and orders. Invoices must be kept for 10 years (Swiss retention rules for business records). Customer data is personal data (Swiss nDSG): only the data needed is stored, and the design covers access and deletion requests within the retention rules.
- **Dependencies**: libraries to write PDF and .xlsx files in the backend (e.g. OpenPDF or PDFBox, Apache POI).
- **Hardware**: at least one Epson TM-m30III receipt printer per salon, set up like event printers.
- **Event setup**: no change in behavior. Event mode stays the default, and existing data stays valid.
- **Depends on** the in-flight changes `add-pos-ordering-system`, `add-table-management`, and `harden-printing-for-events`.
