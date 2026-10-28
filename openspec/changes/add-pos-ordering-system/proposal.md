# Proposal

## Why

The restaurant needs its own point-of-sale (Kassensoftware) so that waiters can take orders on their phones at the table. Today nothing exists, so there is no structured menu, no digital order flow, no automatic kitchen and bar tickets, and no data to evaluate sales. This change sets up the first working version: menu management, order taking (web UI first, mobile apps later), split ticket printing, simple payment, and a CSV export.

## What Changes

- New admin functions to create, edit, deactivate, and categorize menu items (food and drinks) with prices.
- Images can be uploaded per menu item and are shown as tiles in the waiter's order screen to make ordering faster.
- New waiter UI, delivered in two phases: first as a **web UI running on localhost**, then as apps for **Android and iOS** from the same codebase. Waiters log in with their own staff account and PIN.
- Waiters take orders for a table. An order can contain food and drink items, with quantities and optional notes.
- When an order (or a later addition to it) is sent, the backend prints tickets on network-connected **Epson TM-m30III** printers. Drink lines go to the bar printer and food lines go to the kitchen printer.
- Simple payment: an order can be settled as **cash** or **card**, which records the amount and closes the order. No card-terminal integration and no fiscal receipts.
- Admins can generate a **CSV export** of orders (with lines and payment status) for a date range.
- Greenfield system: new backend service, database, and a web UI (admin and waiter), later also the mobile apps. Nothing existing is affected.

## Capabilities

### New Capabilities
- `staff-access`: Staff accounts, roles (admin, waiter), login from the waiter UI (web first, later Android/iOS) and the admin UI, and permission checks.
- `menu-catalog`: Food and drink items, categories, prices, availability, and item images.
- `order-taking`: Creating orders per table, adding and removing lines, notes, sending orders, and order lifecycle.
- `ticket-printing`: Routing sent order lines by item type to the configured Epson TM-m30III printers, ticket content, and handling print failures and reprints.
- `order-payment`: Settling an order as cash or card and closing it.
- `order-export`: CSV export of orders and order lines for a selected period.

### Modified Capabilities
<!-- None: no existing specs in this project. -->

## Impact

- **New code**: backend API service, relational database schema, image storage, and one Flutter UI (waiter and admin) built for web first and for Android and iOS in a later phase. The stack (Java/Spring Boot, PostgreSQL, Flutter) is decided in `design.md` D1.
- **Hardware/network**: two (or more) Epson TM-m30III printers on the local network with ePOS-Print (HTTP/XML) enabled, and static IPs or DHCP reservations. The backend must be able to reach them on the LAN.
- **Distribution**: phase 1 needs no distribution (localhost). In the mobile phase, the app must be distributed to Android and iOS devices (store, MDM, or TestFlight/internal track).
- **Data**: order and payment data is stored persistently and can be exported. Basic data-retention and backup considerations apply.
- **Out of scope**: card-terminal integration, fiscal/legal receipts (e.g. a fiscal printer or TSE), inventory, table floor plans, and customer-facing receipts.
