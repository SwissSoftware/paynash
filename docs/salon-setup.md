# Salon setup and daily use

Paynash can run a beauty salon instead of an event or restaurant (change `add-salon-mode`). A salon sells
**services** to **customers**, gives them an **invoice** (receipt and A4 PDF), and looks at a **monthly report** with
an Excel export. Tables, kitchen and bar tickets, and print stations are not used.

One installation runs either as an event/restaurant or as a salon, never both. Each salon gets its own installation.

## 1. Install

Install the server and the apps as described in [operations.md](operations.md), sections 1 and 2
(server, HTTPS certificate, mobile apps, first admin). A small mini PC at the counter is enough.

## 2. Switch to salon mode

Do this **before the first order**. Once an order exists, the mode can no longer change.

1. Log in as admin and open **Settings**.
2. Under **Mode**, choose **Salon** and confirm.
3. All devices show the salon functions after their next refresh (or when the app comes back to the foreground).

In salon mode, waiters are called **staff**, menu items are called **services**, and sending an order is called
**confirming** it. The stored roles stay the same.

## 3. Business details and VAT

Still in **Settings**:

- **Business name, street, postal code, city** are required. Without them, no invoice can be issued.
- **Phone, email, website** are optional and printed on the invoice.
- **IBAN and account holder** are optional. If set, they are printed at the bottom of the invoice. Only Swiss and
  Liechtenstein IBANs are accepted.
- **Show VAT on invoices**: turn it on if the salon is VAT-registered. It needs the VAT number in the form
  `CHE-123.456.789 MWST`. Prices always include VAT; the invoice shows net, VAT, and gross per rate.

Changes apply to invoices issued **from now on**. Issued invoices keep the details they were issued with.

## 4. Receipt printer

Set up an Epson TM-m30III as in [printing.md](printing.md), *Printer setup*. Then, under **Printers**, add it with the
type **Receipt** and press **Test print**. Only one receipt printer can be active. How receipts behave (waiting for
paper, unclear receipts, copies) is described in [printing.md](printing.md), *Receipts in salon mode*.

## 5. Services and staff

- **Services setup**: create categories (e.g. "Face", "Hands") and services with price and, optionally, the duration
  in minutes. With VAT on, choose the VAT rate per service (8.1 % standard, 2.6 %, 3.8 %, or 0 %).
- **Staff**: create an account for every person at the counter with the role **Staff** (or **Admin** for the owner).
  Staff can sell, manage customers, issue invoices, and mark them paid. Only admins can cancel invoices, undo
  payments, change settings, and see the report.

## 6. Daily use

### Sell a service

1. **New order** → tap **Customer** and pick the customer. Search by name, number, phone, or email. For a new
   customer, tap **New customer**; a name is enough. If the customer may already exist, the app shows the match.
2. Tap the services. Add a note to a line if needed (e.g. "sensitive skin").
3. Tap **Confirm and invoice**. The order is confirmed, the invoice is issued, and the receipt prints.
   - If the invoice could not be issued (e.g. the business address is missing), the order stays confirmed and the
     app says why. Fix the cause and issue the invoice from **History**.
   - **Confirm only, invoice later** confirms without an invoice. Such orders are marked *not invoiced* in
     **History**. They do not count as revenue until they are invoiced.

### Payment

Open the invoice (from **Invoices**, the order in **History**, or the customer) and tap **Mark as paid**. Choose cash,
card, TWINT, or bank transfer, and the payment date (today by default). For a bank transfer, mark the invoice paid on
the day the money arrives.

### Copy and PDF

- **Print copy** prints the receipt again, marked `KOPIE`.
- **Download PDF** creates the A4 invoice. On the web it is downloaded; on a phone or tablet the share sheet opens,
  so you can print it, save it, or send it by email. The customer address fits the window of a C5 envelope.

### Mistakes

- **Wrong service, invoice not yet issued**: an admin cancels the line or voids the order in **History**.
- **Invoice already issued**: an admin opens the invoice and taps **Cancel invoice** with a reason. The number stays
  used and the invoice is marked *cancelled*. Then correct the order in **History** and issue a new invoice.
- **Paid by mistake**: an admin taps **Undo payment** with a reason. The removed payment stays visible in the history.
  A paid invoice must be set back to open before it can be cancelled.

## 7. Month end: report and Excel

Admins open **Report**. It shows the current month; the arrows go to other months, **Date range** picks any period of
up to 366 days.

- **Summary**: revenue, number of invoices, paid and open amounts, revenue per payment method, and VAT per rate.
- **Days, Services, Customers**: revenue per day (also days without revenue), per service, and per customer.
- **Excel** downloads `Rapport_<from>_<to>.xlsx` with the sheets Übersicht, Tage, Dienstleistungen, Kunden,
  Rechnungen (including cancelled invoices), and Positionen. Amounts are numbers and dates are dates, so the file can
  be summed and filtered directly in Excel.

Revenue is the total of the invoices that are **not cancelled**, counted on their **invoice date**.

## 8. Customer data (data protection)

Customer records are personal data (Swiss nDSG). Store only what you need.

- **Access request**: an admin opens the customer → menu → **Export data**. The file contains the customer record and
  all their invoices and payments.
- **Deletion request**: an admin opens the customer → menu → **Delete**. A customer without orders is removed. A
  customer with orders is **anonymized**: name and contact details are removed and the customer appears as "Deleted
  customer" in reports. Issued invoices keep their copy of the customer details, because invoices must be kept.
- Customers who no longer come can be **deactivated**; they are no longer offered when selling.

## 9. Backups and the 10-year retention

Invoices are business records and must be kept for **10 years**. Paynash never deletes invoices, so the database is
the archive. Protect it:

- Set up the daily backup as in [operations.md](operations.md), *Backup and restore*, and set `POS_BACKUP_DIR` to
  external storage (NAS or USB disk). The server alone is not enough.
- Keep an **off-site copy** as well, e.g. copy the latest backup to a second disk that is stored elsewhere once a
  month. A fire or theft at the salon must not destroy all copies.
- Once a year, export the report for the whole year (1 January to 31 December) as Excel and keep it with the
  bookkeeping documents.
