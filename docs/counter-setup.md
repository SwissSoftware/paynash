# Counter sales setup

Guide for the business template **Counter sales** (change `configurable-business-features`): a café, bakery, kiosk,
or shop that sells over the counter. Orders have **no table and no customer**, and nobody needs a print station.
Receipts for guests are optional (section 6). Other templates: [event-setup.md](event-setup.md) (Event and
Restaurant) and [salon-setup.md](salon-setup.md) (Salon).

## 1. Install

Install the server and the apps as described in [operations.md](operations.md), sections 1 and 2 (server, HTTPS
certificate, mobile apps, first admin). A small mini PC behind the counter is enough.

## 2. Apply the Counter sales template

1. Log in as admin and open **Settings**.
2. Under **Business template**, tap **Counter sales** and confirm. All features are switched off: **Tables**,
   **Stations**, **Customers**, **Invoices**, and **Sales report**.
3. All devices show the new functions after their next refresh (or when the app comes back to the foreground).

The template names waiters **Staff**, menu items **Menu**, and sending an order **Sell**. Rename them under **Terms**
if your shop uses other words; the stored roles stay the same.

Need more later? Switch single features on under **Features**: **Invoices** for receipts (also without customers,
see section 6), **Sales report** for the monthly report (needs Invoices), and **Customers** for regular customers.
Switching a feature off again hides it but keeps all data.

## 3. Menu

Under **Menu setup**, create categories (for example "Coffee", "Bakery") and items. Items of all types (food, drink,
service) can be mixed.

## 4. Printers (optional)

A counter usually needs no kitchen tickets. If a barista or the back room should get tickets, add a printer under
**Printers**, switch on **Tickets**, and assign the categories it prints (see [printing.md](printing.md)). Categories
without a printer are simply not printed.

## 5. Daily use

1. Open **New order**. There is no table field and no customer field.
2. Tap the items the customer buys. Change quantities or add a note on the order line.
3. Tap **Sell**. The order is saved and appears in the **History**; the screen is empty for the next customer.

Cancelling a line or voiding a sold order works as in [event-setup.md](event-setup.md) and needs an admin.

## 6. Receipts without customer (optional)

Switch on **Invoices** (and, for the monthly report, **Sales report**) under **Settings → Features**, and fill in the
business details (name, street, postal code, city). Customers can stay off (change `invoices-without-customer`).

- **Sell and invoice** (the button below **Sell**) saves the order and prints a receipt without recipient on every
  printer with **Receipts** on (see [printing.md](printing.md)). The A4 PDF is available in **Invoices**.
- A receipt can also be issued later: open the order in the **History** and tap **Issue invoice**.
- **Limit CHF 400.00**: Swiss VAT law allows a receipt without the recipient's name and address only up to
  CHF 400.00. Above that, Paynash sells the order but issues no invoice and shows "Invoices above CHF 400.00 need a
  customer". For such amounts, switch on **Customers** and pick the customer in the order screen before selling. A
  customer cannot be added to an order that is already sold: an admin voids it, and it is sold again with the
  customer.
- In the sales report, all receipts without customer appear as one entry **Ohne Kunde**.
