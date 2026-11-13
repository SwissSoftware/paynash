import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/recording_file_saver.dart';

Future<AppHarness> start(WidgetTester tester, {bool admin = true}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.salon.useSalon();
  b.salon.report = {
    'summary': {
      'revenue': '530.00',
      'invoiceCount': 5,
      'paid': '290.00',
      'open': '240.00',
      'byMethod': [
        {'method': 'cash', 'amount': '120.00'},
        {'method': 'card', 'amount': '120.00'},
        {'method': 'twint', 'amount': '50.00'},
        {'method': 'bank_transfer', 'amount': '0.00'},
      ],
      'vat': [
        {'rate': '8.1', 'gross': '120.00', 'net': '111.01', 'vat': '8.99'},
      ],
    },
    'days': [
      {'date': '2026-10-01', 'revenue': '120.00', 'invoiceCount': 1},
      {'date': '2026-10-02', 'revenue': '0.00', 'invoiceCount': 0},
    ],
    'services': [
      {'name': 'Facial', 'quantity': 3, 'revenue': '360.00'},
    ],
    'customers': [
      {'number': 2, 'name': 'Peter Meili', 'invoiceCount': 2, 'revenue': '300.00'},
      {'number': 3, 'name': 'Deleted customer', 'invoiceCount': 1, 'revenue': '60.00'},
    ],
  };
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return app;
}

Finder nav(String label) =>
    find.descendant(of: find.byType(NavigationRail), matching: find.text(label));

void main() {
  testWidgets('staff do not see the report', (tester) async {
    await start(tester, admin: false);
    expect(nav('Report'), findsNothing);
  });

  testWidgets('shows the summary of the current month and the lists', (tester) async {
    final app = await start(tester);
    await tester.tap(nav('Report'));
    await tester.pumpAndSettle();

    final now = DateTime.now();
    final last = DateTime(now.year, now.month + 1, 0);
    String two(int v) => v.toString().padLeft(2, '0');
    expect(app.backend.salon.reportRequests.single,
        '${now.year}-${two(now.month)}-01..${now.year}-${two(now.month)}-${two(last.day)}');
    expect(tester.widget<Text>(find.byKey(const Key('report-revenue'))).data, '530.00');
    expect(tester.widget<Text>(find.byKey(const Key('report-paid'))).data, '290.00');
    expect(tester.widget<Text>(find.byKey(const Key('report-open'))).data, '240.00');
    expect(find.text('Paid by: Cash 120.00 · Card 120.00 · TWINT 50.00'), findsOneWidget);
    expect(find.text('VAT 8.1 %: 8.99 (net 111.01, gross 120.00)'), findsOneWidget);

    expect(find.text('01.10.2026'), findsOneWidget);
    expect(find.text('02.10.2026'), findsOneWidget, reason: 'days without revenue are listed');
    expect(find.text('CHF 0.00'), findsOneWidget);

    await tester.tap(find.byKey(const Key('report-tab-services')));
    await tester.pumpAndSettle();
    expect(find.text('Facial'), findsOneWidget);
    expect(find.text('3 ×'), findsOneWidget);

    await tester.tap(find.byKey(const Key('report-tab-customers')));
    await tester.pumpAndSettle();
    expect(find.text('Deleted customer'), findsOneWidget);
    expect(find.text('No. 3 · 1 invoices'), findsOneWidget);
  });

  testWidgets('previous month and Excel download', (tester) async {
    final saver = RecordingFileSaver.install();
    final app = await start(tester);
    await tester.tap(nav('Report'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('report-prev')));
    await tester.pumpAndSettle();

    final now = DateTime.now();
    final prev = DateTime(now.year, now.month - 1);
    final last = DateTime(prev.year, prev.month + 1, 0);
    String two(int v) => v.toString().padLeft(2, '0');
    final period = '${prev.year}-${two(prev.month)}-01..${prev.year}-${two(prev.month)}-${two(last.day)}';
    expect(app.backend.salon.reportRequests.last, period);

    await tester.tap(find.byKey(const Key('report-excel')));
    await tester.pumpAndSettle();
    expect(saver.saved.single.name, 'Rapport_${period.replaceAll('..', '_')}.xlsx');
  });
}
