import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

/// Logs in on the History tab (or as an admin), with Cola and Margherita on
/// the menu.
Future<(AppHarness, int, int)> start(WidgetTester tester, {bool admin = false}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  final cola = b.addItem(b.addCategory('Drinks'), 'Cola', 'drink', '4.50');
  final pizza = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00');
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return (app, cola, pizza);
}

Future<void> open(WidgetTester tester, String section) async {
  await tester.tap(find.text(section));
  await tester.pumpAndSettle();
}

void main() {
  group('print status', () {
    testWidgets('a failed ticket is flagged in the history with its error', (tester) async {
      final (app, cola, pizza) = await start(tester);
      final anna = app.backend.staff.first;
      final failed = app.backend.addSentOrder('12', anna, [(pizza, 1)]);
      app.backend.addTicket(failed, status: 'failed', error: 'printer offline');
      final ok = app.backend.addSentOrder('3', anna, [(cola, 1)]);
      app.backend.addTicket(ok, type: 'drink', printer: 'Bar');

      await open(tester, 'History');

      expect(find.textContaining('Ticket failed: printer offline'), findsOneWidget);
      final failedIcon = tester.widget<Icon>(find.byKey(Key('print-status-${failed['id']}')));
      expect(failedIcon.icon, Icons.print_disabled);
      final okIcon = tester.widget<Icon>(find.byKey(Key('print-status-${ok['id']}')));
      expect(okIcon.icon, Icons.print);
    });

    testWidgets('a waiter reprints a failed ticket and the warning goes away', (tester) async {
      final (app, _, pizza) = await start(tester);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [(pizza, 1)]);
      final ticket = app.backend.addTicket(order, status: 'failed', error: 'paper end');
      await open(tester, 'History');
      await tester.tap(find.byKey(Key('history-order-${order['id']}')));
      await tester.pumpAndSettle();

      expect(find.text('Kitchen ticket failed: paper end'), findsOneWidget);
      await tester.tap(find.byKey(Key('print-warning-reprint-${ticket['id']}')));
      await tester.pumpAndSettle();

      final tickets = order['tickets'] as List;
      expect(tickets, hasLength(2));
      expect((tickets.last as Map)['kind'], 'reprint');
      expect(find.byKey(Key('print-warning-${ticket['id']}')), findsNothing);
      expect(find.text('Failed: paper end · reprinted'), findsOneWidget);
      expect(find.text('Kitchen reprint'), findsOneWidget);

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.textContaining('Ticket failed'), findsNothing);
    });

    testWidgets('a ticket still printing cannot be reprinted yet', (tester) async {
      final (app, cola, _) = await start(tester);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [(cola, 1)]);
      final ticket = app.backend.addTicket(order, type: 'drink', printer: 'Bar', status: 'pending');
      await open(tester, 'History');
      await tester.tap(find.byKey(Key('history-order-${order['id']}')));
      await tester.pumpAndSettle();

      expect(find.text('Printing…'), findsOneWidget);
      final button = tester.widget<TextButton>(find.byKey(Key('ticket-reprint-${ticket['id']}')));
      expect(button.onPressed, isNull);
    });

    testWidgets('without a printer for the type the ticket says so', (tester) async {
      final (app, _, pizza) = await start(tester);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [(pizza, 1)]);
      app.backend.addTicket(order, printer: null, status: 'failed', error: 'no printer configured');
      await open(tester, 'History');
      await tester.tap(find.byKey(Key('history-order-${order['id']}')));
      await tester.pumpAndSettle();

      expect(find.text('Food ticket failed: no printer configured'), findsOneWidget);
    });
  });

  group('printer setup', () {
    testWidgets('an admin adds the bar printer and runs a test print', (tester) async {
      final (app, _, _) = await start(tester, admin: true);
      app.backend.addPrinter('Kitchen', 'food');
      await open(tester, 'Printers');
      expect(find.byKey(const Key('printer-missing-drink')), findsOneWidget);

      await tester.tap(find.byKey(const Key('printer-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('printer-name')), 'Bar');
      await tester.enterText(find.byKey(const Key('printer-address')), '192.168.1.51');
      await tester.tap(find.text('Drinks (bar)'));
      await tester.tap(find.byKey(const Key('printer-save')));
      await tester.pumpAndSettle();

      expect(app.backend.printers.last, containsPair('type', 'drink'));
      expect(find.byKey(const Key('printer-Bar')), findsOneWidget);
      expect(find.byKey(const Key('printer-missing-drink')), findsNothing);

      await tester.tap(find.byKey(const Key('printer-test-Bar')));
      await tester.pumpAndSettle();
      expect(app.backend.testPrints, ['Bar']);
      expect(find.text('Test print sent to Bar'), findsOneWidget);
    });

    testWidgets('a failed test print shows the printer error', (tester) async {
      final (app, _, _) = await start(tester, admin: true);
      app.backend.addPrinter('Kitchen', 'food');
      app.backend.testPrintErrors['Kitchen'] = 'cover open';
      await open(tester, 'Printers');

      await tester.tap(find.byKey(const Key('printer-test-Kitchen')));
      await tester.pumpAndSettle();

      expect(find.text('Test print on Kitchen failed: cover open'), findsOneWidget);
    });

    testWidgets('a second active printer for a type is refused', (tester) async {
      final (app, _, _) = await start(tester, admin: true);
      app.backend.addPrinter('Kitchen', 'food');
      await open(tester, 'Printers');

      await tester.tap(find.byKey(const Key('printer-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('printer-name')), 'Kitchen 2');
      await tester.enterText(find.byKey(const Key('printer-address')), '192.168.1.52');
      await tester.tap(find.byKey(const Key('printer-save')));
      await tester.pumpAndSettle();

      expect(find.text('"Kitchen" already prints food. Deactivate it first.'), findsOneWidget);
      expect(app.backend.printers, hasLength(1));
    });

    testWidgets('waiters do not see the printer setup', (tester) async {
      await start(tester);
      expect(find.text('Printers'), findsNothing);
    });
  });
}
