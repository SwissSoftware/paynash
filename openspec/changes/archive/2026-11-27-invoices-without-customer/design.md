# Design

## Context

See proposal.md for the motivation and specs/ for the requirements. Current state:

- `invoice.customer_id BIGINT NOT NULL REFERENCES customer` and `invoice.customer JSONB NOT NULL` (V13). `Invoice` carries `long customerId` and a non-null `CustomerCopy`. `InvoiceService.issue` rejects orders without customer ("The order needs a customer to be invoiced") and builds the copy from `CustomerRepository`.
- `ReceiptRenderer` and `InvoicePdf` always print the customer block (number, name, address).
- `ReportService` joins `invoice i JOIN customer cu ON cu.id = i.customer_id` for the customer list, the invoice rows, and the line rows, so invoices without customer would silently disappear from three report sections.
- `InvoiceRepository.list` reads `(i.customer->>'number')::int` and the app's `InvoiceSummary`/`InvoiceData` require `customerNumber` as `int`.
- `OrderService` shows the invoice of an order (`shownInvoice`) and blocks cancelling lines and voiding (spec invoicing, "Invoices are final") only when `order.customerId() != null` (around line 92 and line 221).
- `Feature.INVOICES` requires `CUSTOMERS`; V16 added the check `installation_settings_invoices_need_customers`.
- App: `OrderScreen` issues the invoice on send only with a customer (`_invoiceOnSend`), and `order_detail_screen` offers "Issue invoice" only for `order.hasCustomer`.

## Goals / Non-Goals

**Goals:**
- An invoice without customer is a complete, valid invoice everywhere: issue, receipt, PDF, list, payments, cancel, report, Excel.
- The CHF 400.00 limit is enforced in one place on the server, before an invoice number is drawn.

**Non-Goals:**
- Adding a customer to an existing invoice. An invoice stays final; a wrong one is cancelled and issued again.
- A configurable limit.

## Decisions

**D1 Nullable customer on the invoice (V17).**
`ALTER TABLE invoice ALTER COLUMN customer_id DROP NOT NULL, ALTER COLUMN customer DROP NOT NULL` plus `CHECK ((customer_id IS NULL) = (customer IS NULL))`, so a copy exists exactly when a customer exists. V17 also drops `installation_settings_invoices_need_customers`. In Java, `Invoice.customerId` becomes `Long` and `customer` may be null (`hasCustomer()` helper). Alternative: a placeholder customer "Barverkauf" per installation was rejected; it would mix into customer management, history, data protection deletion, and the report.

**D2 Limit check in `InvoiceService.issue`, before numbering.**
After the order lock and the existing checks, and after computing the total from the lines that are not cancelled, an order without customer and a total above 40000 Rappen is rejected with 409 and `code: customer_required`, detail "Invoices above CHF 400.00 need a customer". The check comes before `invoice_counter` is incremented, so no number is used (spec scenario "Above the limit"). The limit is a constant `SMALL_AMOUNT_LIMIT_RAPPEN = 40_000` with a comment pointing to Swiss VAT law. It compares the gross total, which is what the receipt shows.

**D3 Receipt and PDF without recipient.**
`ReceiptRenderer` skips the lines "Kunde Nr." and the address when `invoice.customer()` is null. `InvoicePdf` leaves the envelope window empty. All other blocks keep their position, so receipts with and without customer look alike. Covered by rendering tests that assert the absence of "Kunde Nr.".

**D4 Report with outer joins and one group "Ohne Kunde".**
`ReportService` uses `LEFT JOIN customer`. The customer list groups `customer_id IS NULL` into one row with name "Ohne Kunde" and no number; the invoice and line rows show an empty customer. `SalesReport` customer number becomes nullable (`Integer`). The Excel sheets write an empty cell for the number. Totals are unchanged because they are already summed from `invoice`, not from the join.

**D5 Invoice of every order in history and the finality check.**
`OrderService` reads `shownInvoice` for every order (not only with customer) and applies the "Invoices are final" check to every order. `OrderRepository.history` already reads the invoice per order; it is passed through regardless of the customer.

**D6 App: send and invoice without customer.**
`OrderScreen`: while invoices are on, the secondary action "<send> and invoice" is always available. With a customer picked, the main button stays "<send> and invoice" as today; without customer, the main button is "<send>" and the secondary one "<send> and invoice". The rejection above CHF 400 arrives as the existing "sent, but the invoice was not issued" message, which now carries the server's detail. `order_detail_screen` offers "Issue invoice" for every sent, not invoiced order while invoices are on. Invoice screens show "Ohne Kunde" in place of the customer and hide the number. The settings screen no longer shows "Needs customers" under Invoices.

## Risks / Trade-offs

- [Staff hit the limit at the counter and the guest waits] → The message names the reason and the order stays sent; staff pick or create the customer and issue the invoice from the history.
- [The limit is VAT law, not a technical constraint, and could change] → One constant, one test; changing it is a one-line change with a spec update.
- [Existing invoices all have a customer, so the new NULL handling is only exercised by new data] → Upgrade test migrates a database with invoices from V16 to V17 and issues one with and one without customer afterwards.
- [Anonymizing a customer still keeps the invoice copy] → Unchanged behaviour for invoices with customer. Invoices without customer hold no personal data at all.
