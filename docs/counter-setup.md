# Counter sales setup

Guide for the business template **Counter sales** (change `configurable-business-features`): a café, bakery, kiosk,
or shop that sells over the counter. Orders have **no table and no customer**, there are no invoices, and nobody
needs a print station. Other templates: [event-setup.md](event-setup.md) (Event and Restaurant) and
[salon-setup.md](salon-setup.md) (Salon).

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

Need more later? Switch single features on under **Features**: **Customers** for regular customers, then
**Invoices** for invoices and receipts, then **Sales report**. Switching a feature off again hides it but keeps all
data.

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
