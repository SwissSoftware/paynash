# Tasks

Moved unchanged from the origin changes so those can be archived. The origin is given in brackets. Printer tasks need `fix-epos-http-version` to print on the real printer first.

## 1. Printer hardware

- [ ] 1.1 [add-pos-ordering-system 5.8] Run a hardware test with a real Epson TM-m30III: enable ePOS-Print, run a test print, check umlauts (ä, ö, ü, é), the cut, and readability; test paper end and cover open. Verify with photos/notes of the test tickets in `docs/printing.md`, and adjust the code page/transliteration if needed
- [ ] 1.2 [harden-printing-for-events 4.2] After the hardware test (1.1): if ePOS returns status bits without printing, report paper end, cover open, and paper near end from the probe; otherwise record in `docs/printing.md` that only offline is detected while idle. Verify against the real TM-m30III and record the result in `docs/printing.md`

## 2. Guides

- [ ] 2.1 [add-pos-ordering-system 5.9] Document printer setup (static IP, enable ePOS, device ID) in `docs/printing.md` and verify that the steps can be reproduced on a reset printer
- [ ] 2.2 [add-pos-ordering-system 8.3] Write the operations guide `docs/operations.md` (installation, first admin, printer setup, update, backup/restore, troubleshooting) and verify that a second person can install it using only the guide
- [ ] 2.3 [harden-printing-for-events 9.1] Write `docs/event-setup.md`: network layout (router, switch, APs, wired printers and server recommended, separate 5 GHz staff SSID, fixed IPs), WiFi printer settings (DHCP reservation, power saving off, signal check), a UPS for router, switch, server, and printers, and station tablet setup (charger, kiosk/guided mode, sound armed). Verify by setting up one station tablet using only the guide
- [ ] 2.4 [add-salon-mode 16.1] Write `docs/salon-setup.md`: installation, switching to salon mode, business details and VAT, receipt printer setup, services, staff, daily use (confirm and invoice, payment, copy, cancel), month-end report and Excel, data protection requests, and backups with an off-site copy for the 10-year retention. Verify by setting up a fresh installation as a salon using only the guide

## 3. Automated checks

- [ ] 3.1 [harden-printing-for-events 10.3] Run backend tests, `flutter analyze`, and `flutter test`, and verify that everything passes
- [ ] 3.2 [printer-category-routing 8.1] Run `./mvnw verify` and `flutter analyze && flutter test`, then manually check with the docker setup: in salon mode add a receipt printer and issue an invoice; in event mode assign a category to two printers and send, cancel and reprint an order. Verify that the tickets appear on the expected printers or stations

## 4. On-site acceptance

- [ ] 4.1 [add-pos-ordering-system 9.1] Run the full flow on site with the web UI and real printers: admin creates items with images, two waiters in two browser sessions take a mixed order for the same table, the bar printer receives only drinks and the kitchen printer only food, a follow-up order for the same table is sent and printed, both orders appear in the order history, an admin cancels a line and the STORNO ticket is printed, and the CSV export contains all lines correctly. Record the result in `docs/acceptance.md`
- [ ] 4.2 [add-pos-ordering-system 9.2] Run the failure test on site: unplug the kitchen printer, send an order, see the failed status in the app, reconnect it, and reprint with "REPRINT". Verify that no ticket is lost or duplicated, and record the result in `docs/acceptance.md`
- [ ] 4.3 [harden-printing-for-events 10.4] On site with real printers and station tablets: send orders from several waiters, pull paper, open the cover, unplug a printer's network, switch one printer to WiFi and drop it while printing, and restart the backend during printing. Verify that no ticket is lost or printed twice unnoticed, and record the results in `docs/acceptance.md`
- [ ] 4.4 [add-salon-mode 17.2] End-to-end test with a real TM-m30III: set up a salon, create customers and services with VAT on, confirm and invoice several orders from web and a phone, pull the receipt paper during issuing, cancel and reissue one invoice, mark payments with different methods, and export the month. Verify that receipt, PDF, app report, and Excel show the same numbers, invoice numbers have no gaps, and record the results in `docs/acceptance.md`
- [ ] 4.5 [add-salon-mode 17.3] Regression check in event mode: run the event acceptance steps from 4.3 on the upgraded version. Verify that tickets, stations, and exports behave as before

## 5. Android and iOS

- [ ] 5.1 [add-pos-ordering-system 10.1] Add the Android and iOS targets to the Flutter app with a configurable backend URL. Verify that the app starts on an Android emulator and an iOS simulator against the HTTPS backend
- [ ] 5.2 [add-pos-ordering-system 10.2] Implement the token storage interface with secure storage (Keychain/Keystore). Verify login/logout manually on Android and iOS
- [ ] 5.3 [add-pos-ordering-system 10.3] Check the menu tiles, order screen, order history, and print status on both platforms, including the image cache. Verify manually on both platforms that an order sent on one device appears in the history on the other (≤ 5 s)
- [ ] 5.4 [add-pos-ordering-system 10.4] Configure the iOS build (NSLocalNetworkUsageDescription, ATS-compatible HTTPS) and Android release signing, and build for the distribution channel (TestFlight/internal track). Verify that the app installs from that channel on a real device
- [ ] 5.5 [add-salon-mode 14.2] Download the PDF through `export/file_saver` on web, Android, and iOS. Verify on web in a widget test and manually on one Android and one iOS device
- [ ] 5.6 [add-pos-ordering-system 10.5] Repeat the on-site flow from 4.1 with a waiter on Android and a waiter on iOS, and record the result in `docs/acceptance.md`
