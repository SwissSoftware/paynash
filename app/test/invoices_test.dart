import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/recording_file_saver.dart';

/// A salon with three invoices of November 2026 and one of October:
/// 2026-0001 Laura (October), 2026-0002 Laura open, 2026-0003 Peter paid,
/// 2026-0004 Peter cancelled.
Future<AppHarness> start(WidgetTester tester, {bool admin = false, bool printer = true}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.salon.useSalon();
  if (printer) b.addPrinter('Counter', 'receipt');
  final laura = b.salon.addCustomer('Laura', 'Meier');
  final peter = b.salon.addCustomer('Peter', 'Meili');
  Map<String, dynamic> invoice(int id, String number, int customer, String name, String date,
          String status) =>
      {
        'id': id,
        'number': number,
        'orderId': id + 100,
        'customerId': customer,
        'customerName': name,
        'date': date,
        'status': status,
        'lines': <Map<String, dynamic>>[
          {'qty': 1, 'name': 'Facial', 'unitPrice': '120.00', 'vatRate': '8.1', 'amount': '120.00',
           'note': null},
        ],
        'total': '120.00',
        'vatEnabled': false,
        'vatUid': null,
        'vat': <Object>[],
        'cancelReason': status == 'cancelled' ? 'duplicate' : null,
        'cancelledBy': status == 'cancelled' ? 'Boss' : null,
        'paymentHistory': <Map<String, dynamic>>[
          if (status == 'paid')
            {'method': 'card', 'paidOn': date, 'recordedBy': 'Anna', 'removedBy': null,
             'removeReason': null},
        ],
        'receipt': {'kind': 'normal', 'type': 'receipt', 'status': 'printed'},
      };
  b.salon.invoices.addAll([
    invoice(1, '2026-0001', laura, 'Laura Meier', '2026-10-30', 'open'),
    invoice(2, '2026-0002', laura, 'Laura Meier', '2026-11-02', 'open'),
    invoice(3, '2026-0003', peter, 'Peter Meili', '2026-11-05', 'paid'),
    invoice(4, '2026-0004', peter, 'Peter Meili', '2026-11-06', 'cancelled'),
  ]);
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  await tester.tap(find.descendant(of: find.byType(NavigationRail), matching: find.text('Invoices')));
  await tester.pumpAndSettle();
  return app;
}

Finder listed(String number) => find.byKey(Key('invoice-$number'));

Future<void> openInvoiceTile(WidgetTester tester, String number) async {
  await tester.tap(listed(number));
  await tester.pumpAndSettle();
}

void main() {
  group('list', () {
    testWidgets('shows the current month, newest first, and other months', (tester) async {
      // The screen starts at the real current month; go to November 2026 explicitly.
      await start(tester);
      final now = DateTime.now();
      final months = (2026 - now.year) * 12 + 11 - now.month;
      for (var i = 0; i < months.abs(); i++) {
        await tester.tap(find.byKey(Key(months > 0 ? 'invoices-next-month' : 'invoices-prev-month')));
        await tester.pumpAndSettle();
      }
      expect(find.text('November 2026'), findsOneWidget);
      final shown = tester
          .widgetList<ListTile>(find.byWidgetPredicate((w) => w is ListTile && '${w.key}'.contains('invoice-2026')))
          .map((t) => (t.title as Text).data)
          .toList();
      expect(shown, ['2026-0004 · Peter Meili', '2026-0003 · Peter Meili', '2026-0002 · Laura Meier']);

      await tester.tap(find.byKey(const Key('invoices-filter-open')));
      await tester.pumpAndSettle();
      expect(listed('2026-0002'), findsOneWidget);
      expect(listed('2026-0003'), findsNothing);

      await tester.tap(find.byKey(const Key('invoices-filter-all')));
      await tester.enterText(find.byKey(const Key('invoices-search')), 'meili');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();
      expect(listed('2026-0002'), findsNothing);
      expect(listed('2026-0003'), findsOneWidget);

      await tester.tap(find.byKey(const Key('invoices-prev-month')));
      await tester.enterText(find.byKey(const Key('invoices-search')), '');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();
      expect(find.text('October 2026'), findsOneWidget);
      expect(listed('2026-0001'), findsOneWidget);
    });
  });

  group('invoice view', () {
    Future<void> goToNovember(WidgetTester tester) async {
      final now = DateTime.now();
      final months = (2026 - now.year) * 12 + 11 - now.month;
      for (var i = 0; i < months.abs(); i++) {
        await tester.tap(find.byKey(Key(months > 0 ? 'invoices-next-month' : 'invoices-prev-month')));
        await tester.pumpAndSettle();
      }
    }

    testWidgets('staff mark an open invoice as paid by TWINT', (tester) async {
      final app = await start(tester);
      await goToNovember(tester);
      await openInvoiceTile(tester, '2026-0002');
      expect(find.byKey(const Key('invoice-cancel')), findsNothing, reason: 'admins only');
      await tester.tap(find.byKey(const Key('invoice-pay')));
      await tester.pumpAndSettle();
      final save = tester.widget<FilledButton>(find.byKey(const Key('payment-save')));
      expect(save.onPressed, isNull, reason: 'a method is required');
      await tester.tap(find.byKey(const Key('payment-twint')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('payment-save')));
      await tester.pumpAndSettle();

      final payment = (app.backend.salon.invoice(2)['paymentHistory'] as List).single as Map;
      expect(payment['method'], 'twint');
      expect(find.byKey(const Key('invoice-payment')), findsOneWidget);
      expect(find.textContaining('by TWINT'), findsOneWidget);
    });

    testWidgets('admins cancel open invoices and undo payments', (tester) async {
      final app = await start(tester, admin: true);
      await goToNovember(tester);
      await openInvoiceTile(tester, '2026-0003');
      expect(find.byKey(const Key('invoice-cancel')), findsNothing, reason: 'paid');
      await tester.tap(find.byKey(const Key('invoice-undo-payment')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('dialog-text')), 'wrong invoice marked');
      await tester.tap(find.byKey(const Key('dialog-submit')));
      await tester.pumpAndSettle();
      expect(app.backend.salon.invoice(3)['status'], 'open');
      expect(find.textContaining('Removed payment: Card'), findsOneWidget);

      await tester.tap(find.byKey(const Key('invoice-cancel')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('dialog-text')), 'wrong service');
      await tester.tap(find.byKey(const Key('dialog-submit')));
      await tester.pumpAndSettle();
      expect(app.backend.salon.invoice(3)['status'], 'cancelled');
      expect(find.text('Cancelled by Boss: wrong service'), findsOneWidget);
      expect(find.byKey(const Key('invoice-pay')), findsNothing);
    });

    testWidgets('shows a failed receipt and prints a copy', (tester) async {
      final app = await start(tester, printer: false);
      app.backend.salon.invoice(2)['receipt'] = {
        'kind': 'normal',
        'type': 'receipt',
        'status': 'failed',
        'error': 'no printer configured',
      };
      await goToNovember(tester);
      await openInvoiceTile(tester, '2026-0002');
      expect(find.text('Receipt failed: no printer configured'), findsOneWidget);

      app.backend.addPrinter('Counter', 'receipt');
      await tester.tap(find.byKey(const Key('invoice-print-copy')));
      await tester.pumpAndSettle();
      expect(app.backend.salon.copies, ['2026-0002']);
      expect(find.text('Receipt printed'), findsOneWidget);
    });

    testWidgets('downloads the PDF', (tester) async {
      final saver = RecordingFileSaver.install();
      await start(tester);
      await goToNovember(tester);
      await openInvoiceTile(tester, '2026-0002');
      await tester.tap(find.byKey(const Key('invoice-pdf')));
      await tester.pumpAndSettle();
      expect(saver.saved.single.name, 'Rechnung-2026-0002.pdf');
      expect(saver.saved.single.mimeType, 'application/pdf');
      expect(find.text('Saved Rechnung-2026-0002.pdf'), findsOneWidget);
    });
  });
}
