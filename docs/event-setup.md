# Event setup

How to set up the network, the printers, and the print stations for a festival or any event with many waiters. The
goal: no ticket is lost or printed twice without someone noticing (change `harden-printing-for-events`). Read
[printing.md](printing.md) for the printer setup itself and [operations.md](operations.md) for the server.

## Overview

```
                              +------------------+
  SIM router (internet) ------|      SWITCH      |------ server (backend, database)
                              +------------------+
                                |      |      |
               access point ----+      |      +---- kitchen printer (cable)
               access point -----------+---------- bar printer (cable)
                  ~ ~ ~
   waiter phones, station tablets (WiFi)
```

- Only phones and tablets use WiFi. The server, the printers, and the access points are on cables to the same switch.
- Waiters order and tickets print **without internet**. The internet connection is only needed for updates and,
  later, for guest self-ordering.

## Network

1. **Cable what you can.** Server, printers, and access points go on cables to the same switch. The link from the
   server to the printers then never depends on WiFi.
2. **Access points on cables, not mesh.** Mesh links halve the bandwidth and make the latency jumpy when the area is
   full of visitors' phones.
3. **A staff network of its own.** Create a separate SSID only for staff phones and station tablets, on 5 GHz only.
   Visitors' devices crowd 2.4 GHz. If there is a guest network, keep it separate.
4. **Fast roaming on.** Waiters walk between access points all evening. Turn on fast roaming (802.11r/k/v) if the
   access points offer it.
5. **Fixed addresses.** Give the server and every printer a DHCP reservation (or a static IP outside the DHCP pool).
   The apps reach the server at a fixed name (see [operations.md](operations.md#hostname)).
6. **Test without internet.** Unplug the router's internet (or remove the SIM) and check that waiters can still send
   orders and that tickets print. Some cloud-managed access points need the internet only for management, but test it.

## Printers on WiFi

Cables are recommended, but a printer can be on WiFi when no cable reaches it. The system gives the same guarantees
(see [printing.md](printing.md#printers-on-wifi)):

- Give it a DHCP reservation, as for wired printers.
- Turn off WiFi power saving in the printer's network settings, so it does not doze off between tickets.
- Check the signal at the printer's place during the event, with the area full: send a few test prints from the
  Printers screen.

## Power

A short power cut must not take the system down. Put a small UPS behind:

- the router, the switch, and the server (most important),
- the printers (they need little power),
- the access points, if they are powered over the switch (PoE), they are covered by the switch's UPS.

Generators and fryers that trip a fuse are the most common cause of outages at events.

## Print stations

Put one tablet next to every printer. Station staff see that printer's tickets, get an alarm when something needs
attention, resolve unclear tickets, and can cancel lines with a waiter's PIN.

### Set up a station

1. As admin, open **Printers** and check that every printer is active and shows **Status: ready**.
2. Open **Staff**, press **Add staff**, and enter a username (e.g. `station-bar`), a name (e.g. "Station Bar"), and a
   PIN. Choose the role **Station**, then tap the printer the station shows (e.g. **Bar**). Save.
3. On the tablet, open the app (or `https://<hostname>` in the browser) and log in with the station account. The
   station stays logged in for 24 hours.
4. Tap **Tap to enable sound alerts**. Without this tap the browser plays no sound. Turn the tablet's volume up.
5. Keep the tablet on its charger. The station screen keeps the display on by itself; if the tablet still locks,
   set its screen timeout to "never" or use screen pinning (Android) / Guided Access (iPad).
6. Back on the admin **Printers** screen, check that the station appears under its printer with **last seen** a few
   seconds ago. A station that was not seen for a while has lost its connection or gone to sleep.

### What the station shows

At the top, a chip per printer with its status and the last printed ticket number (e.g. `Bar · ready · last BAR
0141`). Below, today's tickets, newest first: time, ticket number, table, order number, waiter, number of lines, and
status. Every ticket carries its number in large print at the top, so staff can compare the list with the pile.

### Alarms and what to do

The station turns red and beeps every few seconds until the cause is gone:

| Alarm | What to do |
|---|---|
| `Bar: paper end, 3 tickets waiting` | Put in a new roll. The waiting tickets then print by themselves, in order. Nobody needs to reprint them. |
| `Bar: cover open` | Close the cover. Waiting tickets print by themselves. |
| `Bar: offline` | Check the printer's power and cable/WiFi. Waiting tickets print by themselves once it is back. |
| `Bar: 2 tickets waiting for 45 s` | The printer is slow or stuck. Check it; the tickets keep their order. |
| `Bar: 1 unclear ticket, check the pile` | The printer may or may not have printed it. Look for the ticket number in the pile: found it, press **Was printed**; missing, press **Reprint**. |
| `No connection to the server` | The tablet lost the WiFi or the server is down. Check the tablet's WiFi first. |

### Cancelling at the station

Tap a ticket to open its order. **Cancel** next to a line, or **Void order** at the top, asks for a reason and the
username and PIN of the waiter or admin who cancels. A cancellation ticket (STORNO) prints at once. The order shows who
cancelled and at which station. A wrong PIN changes nothing; after five wrong PINs the account is locked for five
minutes.

## Before the event

- [ ] Every printer: test print from the Printers screen, umlauts readable, ticket cut.
- [ ] Every station: logged in, sound enabled, shows its printer as ready, appears as "last seen" for admins.
- [ ] Every waiter: can log in and send a test order; both stations show the ticket.
- [ ] Pull a paper roll during a test order: the station alarms, and the ticket prints by itself after the roll is
  back.
- [ ] Unplug the internet: orders still print.
