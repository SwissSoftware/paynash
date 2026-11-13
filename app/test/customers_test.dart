import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';
import 'support/recording_file_saver.dart';

/// A salon with Laura Meier and Peter Meili; Anna is staff, Boss an admin.
Future<AppHarness> start(WidgetTester tester, {bool admin = false}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.salon.useSalon();
  b.salon.addCustomer('Laura', 'Meier', phone: '079 555 12 34', email: 'laura@example.ch');
  b.salon.addCustomer('Peter', 'Meili');
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  await tester.tap(find.descendant(of: find.byType(NavigationRail), matching: find.text('Customers')));
  await tester.pumpAndSettle();
  return app;
}

Future<void> search(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('customer-search')), text);
  await tester.pump(const Duration(milliseconds: 350));
  await tester.pumpAndSettle();
}

Future<void> fillCustomer(WidgetTester tester, String first, String last) async {
  await tester.tap(find.byKey(const Key('customer-add')));
  await tester.pumpAndSettle();
  await tester.enterText(find.byKey(const Key('customer-firstName')), first);
  await tester.enterText(find.byKey(const Key('customer-lastName')), last);
  await tester.tap(find.byKey(const Key('customer-save')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('lists and searches customers', (tester) async {
    await start(tester);
    expect(find.text('Laura Meier'), findsOneWidget);
    expect(find.text('Peter Meili'), findsOneWidget);
    await search(tester, '079 555');
    expect(find.text('Laura Meier'), findsOneWidget);
    expect(find.text('Peter Meili'), findsNothing);
  });

  testWidgets('creates a customer and opens it', (tester) async {
    final app = await start(tester);
    await fillCustomer(tester, 'Sara', 'Keller');
    expect(app.backend.salon.customers.last['lastName'], 'Keller');
    expect(find.text('Customer no. 3'), findsOneWidget);
    expect(find.text('No invoices yet.'), findsOneWidget);
  });

  testWidgets('warns about a possible duplicate and can create anyway', (tester) async {
    final app = await start(tester);
    await fillCustomer(tester, 'Laura', 'Meier');
    expect(find.byKey(const Key('customer-duplicates')), findsOneWidget);
    expect(find.text('No. 1 · 079 555 12 34 · laura@example.ch'), findsOneWidget);
    await tester.tap(find.byKey(const Key('customer-create-anyway')));
    await tester.pumpAndSettle();
    expect(app.backend.salon.customers.where((c) => c['lastName'] == 'Meier'), hasLength(2));
  });

  testWidgets('picking the existing customer creates nothing', (tester) async {
    final app = await start(tester);
    await fillCustomer(tester, 'Laura', 'Meier');
    await tester.tap(find.byKey(Key('duplicate-${app.backend.salon.customers.first['id']}')));
    await tester.pumpAndSettle();
    expect(app.backend.salon.customers, hasLength(2));
    expect(find.text('Customer no. 1'), findsOneWidget);
  });

  testWidgets('history lists invoices newest first with the revenue', (tester) async {
    final app = AppHarness(tester);
    final b = app.backend;
    b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    b.salon.useSalon();
    final laura = b.salon.addCustomer('Laura', 'Meier');
    b.salon.invoices.addAll([
      {'id': 1, 'number': '2026-0001', 'customerId': laura, 'date': '2026-11-01', 'total': '120.00', 'status': 'paid'},
      {'id': 2, 'number': '2026-0002', 'customerId': laura, 'date': '2026-11-05', 'total': '50.00', 'status': 'open'},
      {'id': 3, 'number': '2026-0003', 'customerId': laura, 'date': '2026-11-10', 'total': '80.00', 'status': 'cancelled'},
    ]);
    await app.start();
    await app.login('anna', '4711');
    await tester.tap(find.descendant(of: find.byType(NavigationRail), matching: find.text('Customers')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Laura Meier'));
    await tester.pumpAndSettle();

    final numbers = tester
        .widgetList<ListTile>(find.byWidgetPredicate((w) => w is ListTile && '${w.key}'.contains('customer-invoice')))
        .map((t) => (t.title as Text).data)
        .toList();
    expect(numbers, ['2026-0003', '2026-0002', '2026-0001']);
    expect(find.text('Revenue CHF 170.00'), findsOneWidget);
    expect(find.text('CHF 80.00 · cancelled'), findsOneWidget);
  });

  testWidgets('delete is only offered to admins', (tester) async {
    await start(tester);
    await tester.tap(find.text('Laura Meier'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('customer-admin-menu')), findsNothing);
  });

  testWidgets('admin deactivates, exports, and deletes a customer', (tester) async {
    final saver = RecordingFileSaver.install();
    final app = await start(tester, admin: true);
    await tester.tap(find.text('Peter Meili'));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('customer-admin-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Deactivate'));
    await tester.pumpAndSettle();
    expect(app.backend.salon.customers.last['active'], isFalse);
    expect(find.text('Customer no. 2 · inactive'), findsOneWidget);

    await tester.tap(find.byKey(const Key('customer-admin-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Export data'));
    await tester.pumpAndSettle();
    expect(saver.saved.single.name, 'Kunde-2.json');

    await tester.tap(find.byKey(const Key('customer-admin-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('customer-delete-confirm')));
    await tester.pumpAndSettle();
    expect(app.backend.salon.customers, hasLength(1));
    expect(find.text('Peter Meili'), findsNothing, reason: 'back on the list');
  });
}
