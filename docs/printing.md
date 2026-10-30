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
- **Retries:** when the printer cannot have printed anything (offline, paper end, cover open), the ticket is retried
  with pauses of 2, 4, 8, then 16 seconds for at least 60 seconds, then marked **failed** with the reason. Settings:
  `pos.printing.retry-window` (default `60s`), `pos.printing.connect-timeout` (`3s`), `pos.printing.epos-timeout`
  (`10s`).
- **No automatic duplicates:** a ticket confirmed as printed is never sent again by itself. When the outcome is unclear
  (the printer took the data but did not answer, or reported a cutter or mechanical error), the ticket is marked
  failed at once with "check the ticket and reprint if needed" instead of being retried.
- **Failed tickets** are flagged in the order history and in the order. Waiters and admins can **Reprint** any ticket
  that is not still printing; the reprint carries a `*** REPRINT ***` banner and goes to the printer that serves the
  ticket's type now.

## Troubleshooting

| Error shown | Meaning | What to do |
|---|---|---|
| printer offline | The server cannot reach the printer | Check power, cable/WiFi, and the IP (status sheet), then reprint |
| paper end | Paper roll is empty | Insert a new roll, then reprint |
| cover open | Printer cover is open | Close the cover, then reprint |
| no printer configured | No active printer for this type | Add or activate the printer under Printers, then reprint |
| device ID not found on the printer | Device ID in the app does not match the printer | Correct the device ID (step 6) |
| ePOS-Print is not enabled on the printer | The printer answered 404 | Enable ePOS-Print (step 5) |
| no answer from printer; check the ticket and reprint if needed | Unclear whether it printed | Check at the printer; reprint only if the ticket is missing |

## Hardware test

Not done yet (task 5.8): run the test print on a real TM-m30III, check that umlauts (ä, ö, ü, é) print correctly, the
cut, and readability, and try paper end and cover open. Record the results and photos of the test tickets here. If
umlauts print wrong, the ticket text needs a different code page or transliteration (design D3, Risks).
