# Printing

Production tickets go to two Epson TM-m30III printers: the **kitchen** printer gets food lines, the **bar** printer
gets drink lines. The backend prints over Epson ePOS-Print (XML over HTTP) in the restaurant LAN, so the server
running the backend must reach both printers' IP addresses (design D2, D3).

## Printer setup

Do this once per printer, e.g. after unpacking or after a factory reset.

1. **Connect the printer to the LAN** with a network cable (or set up WiFi with the Epson TM Utility app).
2. **Give it a fixed address.** Either reserve its IP in the router's DHCP settings (preferred; the printer's MAC
   address is on the status sheet, see step 3) or set a static IP in the printer's web configuration under
   *Network → TCP/IP*. Use an address outside the router's DHCP pool. Example: kitchen `192.168.1.51`, bar
   `192.168.1.50`.
3. **Print the status sheet** to check the address: switch the printer off, hold the *Feed* button, switch it on, and
   release *Feed* when printing starts. The sheet shows the IP address, subnet, and MAC address.
4. **Open the web configuration** at `http://<printer-ip>/` in a browser on the same network. The default login is
   user `epson`; the default password is printed on the label on the bottom of the printer (on older firmware it is
   the printer's serial number). Change the password when asked.
5. **Enable ePOS-Print:** *Configuration → ePOS-Print → Enable*, then *Send* / restart the printer if asked.
6. **Note the device ID:** under *Configuration → ePOS-Print → Device ID* (or *Device Admin*). The built-in printer
   is `local_printer` by default; keep it unless you have a reason to change it.
7. **Check from the server** that ePOS answers (replace the IP):

   ```sh
   curl -s -X POST "http://192.168.1.51/cgi-bin/epos/service.cgi?devid=local_printer&timeout=10000" \
     -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: ""' \
     --data '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print"><text>curl test&#10;</text><cut type="feed"/></epos-print></s:Body></s:Envelope>'
   ```

   The printer prints "curl test" and the answer contains `success="true"`. A `404` means ePOS-Print is not enabled;
   `DeviceNotFound` means the device ID in the URL is wrong.

## Configuration in the app

As admin, open **Printers**:

1. **Add printer** with a name (e.g. "Kitchen"), the address from step 2 (an IP, optionally with `:port`, or a full
   `http://` / `https://` URL), the device ID from step 6, and the type: **Food (kitchen)** or **Drinks (bar)**.
2. Press **Test print**. The printer prints a test ticket with umlauts, and the app says whether it worked or shows
   the printer's error.

Each type is printed by at most one active printer. To swap a printer, deactivate the old one first, then activate the
new one. While no printer is active for a type, those tickets fail at once with "no printer configured"; the order is
still saved and the ticket can be reprinted once a printer is set up. A red banner on the Printers screen shows a
missing type.

## How printing behaves

- Sending an order saves it and queues at most one ticket per printer in the same database transaction. Printing never
  delays or undoes saving an order.
- A worker sends queued tickets every second, one at a time per printer, oldest first, so tickets arrive in order. A
  printer that is down does not hold up the other one.
- **Waiting instead of failing:** when the printer certainly printed nothing (offline, paper end, cover open), the
  ticket stays **pending** with that reason and is tried again with pauses of 2, 4, 8, then 16 seconds, **without a
  time limit**. Later tickets for the same printer wait behind it. Once the printer is ready (paper in, cover closed,
  back online), all waiting tickets print by themselves in their original order; nobody has to reprint them.
  Settings: `pos.printing.connect-timeout` (default `3s`), `pos.printing.epos-timeout` (`10s`).
- **Unclear tickets:** when the printer may have printed the ticket (it took the data but did not answer, the
  connection broke while sending, or it reported a cutter or mechanical error), the ticket is marked **unclear** with
  "check the ticket and reprint if needed". It is never sent again by itself. Staff check the pile and reprint it only
  if it is missing. Unclear tickets do not hold up the tickets behind them.
- **Restart while printing:** a ticket is marked *printing* before it goes to the printer. If the backend stops before
  the printer's answer is recorded, the ticket becomes **unclear** ("system restarted while printing") at the next
  start instead of being printed a second time.
- **Failed tickets:** a ticket is **failed** only when it certainly did not print and cannot print until the
  configuration is fixed (no printer configured, wrong device ID, ePOS-Print disabled, invalid print data).
- **No automatic duplicates:** a ticket confirmed as printed, or unclear, is never sent again by itself. Only an explicit
  reprint prints it again.
- Waiting, unclear, and failed tickets are flagged in the order history and in the order. Waiters and admins can
  **Reprint** any ticket that is not waiting or printing; the reprint carries a `*** REPRINT ***` banner and goes to the
  printer that serves the ticket's type now.

## Receipts in salon mode

In salon mode (change `add-salon-mode`) there are no kitchen or bar tickets: services are never printed. Instead, a
**receipt printer** (also an Epson TM-m30III, set up as in *Printer setup*) prints the invoice for the customer.

- Under **Printers**, add the printer with the type **Receipt**. A salon accepts only receipt printers, an event only
  food and drink printers. At most one receipt printer is active.
- **Issuing an invoice** queues its receipt in the same transaction. It goes through the same queue as tickets, so all
  rules above apply: it **waits** while the printer is offline, out of paper, or open, becomes **unclear** if the printer
  may have printed it, and is never printed twice by itself. The receipt carries the printer's running number on top
  (e.g. `COUNTER 0012`), the invoice in German, and the VAT breakdown if the invoice has VAT.
- The invoice view shows the latest receipt's status and error. **Print copy** prints the receipt again marked
  `*** KOPIE ***` (and `*** STORNIERT ***` for a cancelled invoice). A copy also resolves an unclear receipt. It is
  refused while a receipt of the invoice is still waiting or printing.
- Without an active receipt printer, the invoice is still issued and its receipt is **failed: no printer configured**.
  After adding the printer, print a copy.

## Troubleshooting

| Status and message | Meaning | What to do |
|---|---|---|
| pending: printer offline | The server cannot reach the printer; nothing printed | Check power, cable/WiFi, and the IP (status sheet). Waiting tickets print by themselves once it is reachable |
| pending: paper end | Paper roll is empty; nothing printed | Insert a new roll. Waiting tickets print by themselves |
| pending: cover open | Printer cover is open; nothing printed | Close the cover. Waiting tickets print by themselves |
| unclear: no answer from printer; check the ticket and reprint if needed | Unclear whether it printed | Check at the printer; reprint only if the ticket is missing |
| unclear: cutter error / mechanical error / printer timeout ... | The printer reported an error while it may have been printing | Fix the printer, check the pile, reprint only if the ticket is missing |
| unclear: system restarted while printing ... | The backend stopped while the ticket was at the printer | Check the pile; reprint only if the ticket is missing |
| failed: no printer configured | No active printer for this type (or no receipt printer in a salon) | Add or activate the printer under Printers, then reprint (receipts: print a copy) |
| failed: device ID not found on the printer | Device ID in the app does not match the printer | Correct the device ID (step 6), then reprint |
| failed: ePOS-Print is not enabled on the printer | The printer answered 404 | Enable ePOS-Print (step 5), then reprint |

## Hardware test

Not done yet (task 5.8): run the test print on a real TM-m30III, check that umlauts (ä, ö, ü, é) print correctly, the
cut, and readability, and try paper end and cover open. Record the results and photos of the test tickets here. If
umlauts print wrong, the ticket text needs a different code page or transliteration (design D3, Risks).
