import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

/// A salon with a Facial on the menu, customer Laura Meier, staff Anna, and
/// admin Boss.
Future<(AppHarness, int, int)> start(WidgetTester tester, {bool admin = false}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.salon.useSalon();
  final facial = b.addItem(b.addCategory('Face'), 'Facial', 'service', '120.00', durationMin: 60);
  final laura = b.salon.addCustomer('Laura', 'Meier');
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return (app, facial, laura);
}

Future<void> pickLaura(WidgetTester tester, int laura) async {
  await tester.tap(find.byKey(const Key('order-customer')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(Key('picker-customer-$laura')));
  await tester.pumpAndSettle();
}

Future<void> addFacial(WidgetTester tester, int facial) async {
  await tester.tap(find.byKey(Key('menu-tile-$facial')));
  await tester.pump();
}

Future<void> openHistory(WidgetTester tester) async {
  await tester.tap(find.descendant(of: find.byType(NavigationRail), matching: find.text('History')));
  await tester.pumpAndSettle();
}

void main() {
  group('confirm', () {
    testWidgets('asks for a customer instead of a table', (tester) async {
      final (app, facial, _) = await start(tester);
      expect(find.byKey(const Key('table-input')), findsNothing);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();
      expect(find.text('Choose a customer'), findsWidgets);
      expect(app.backend.orders, isEmpty);
    });

    testWidgets('confirm and invoice issues the invoice in a second step', (tester) async {
      final (app, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      expect(find.text('Laura Meier (No. 1)'), findsOneWidget);
      await addFacial(tester, facial);
      expect(find.text('Confirm and invoice (1)'), findsOneWidget);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();

      expect(app.backend.orders.single['customerId'], laura);
      expect(app.backend.salon.invoices.single['number'], '2026-0001');
      expect(find.textContaining('Invoice 2026-0001 issued'), findsOneWidget);
      expect(find.text('Choose a customer'), findsOneWidget, reason: 'ready for the next customer');
      final writes = app.backend.orderWrites.where((w) => w.path == '/orders');
      expect(writes.single.key, isNotNull);
    });

    testWidgets('confirm only leaves the order without invoice', (tester) async {
      final (app, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-confirm-only')));
      await tester.pumpAndSettle();
      expect(app.backend.orders, hasLength(1));
      expect(app.backend.salon.invoices, isEmpty);
      expect(find.text('Order for Laura Meier confirmed'), findsOneWidget);
    });

    testWidgets('when issuing fails the order stays confirmed and the reason is shown',
        (tester) async {
      final (app, facial, laura) = await start(tester);
      app.backend.salon.settings['street'] = null;
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();

      expect(app.backend.orders, hasLength(1));
      expect(app.backend.salon.invoices, isEmpty);
      expect(
        find.text(
          'Order for Laura Meier confirmed, but the invoice was not issued: business address '
          'missing, ask an admin to complete the settings. Issue it from the history.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('warns when no receipt was printed', (tester) async {
      final (_, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();
      expect(
        find.text('Invoice 2026-0001 issued, but no receipt was printed: no printer configured'),
        findsOneWidget,
      );
    });

    testWidgets('an inactive customer is rejected and must be chosen again', (tester) async {
      final (app, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      app.backend.salon.customer(laura)['active'] = false;
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();
      expect(find.text('Laura Meier is no longer active'), findsOneWidget);
      expect(app.backend.orders, isEmpty);
    });

    testWidgets('creates a new customer from the picker', (tester) async {
      final (app, facial, _) = await start(tester);
      await tester.tap(find.byKey(const Key('order-customer')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('picker-search')), 'Keller');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('picker-new-customer')));
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(find.byKey(const Key('customer-lastName'))).controller!.text,
          'Keller');
      await tester.enterText(find.byKey(const Key('customer-firstName')), 'Sara');
      await tester.tap(find.byKey(const Key('customer-save')));
      await tester.pumpAndSettle();
      expect(find.text('Sara Keller (No. 2)'), findsOneWidget);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-confirm-only')));
      await tester.pumpAndSettle();
      expect(app.backend.orders.single['customerName'], 'Sara Keller');
    });
  });

  group('history', () {
    testWidgets('shows the customer and the invoice status', (tester) async {
      final (app, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-confirm-only')));
      await tester.pumpAndSettle();
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-send')));
      await tester.pumpAndSettle();

      await openHistory(tester);
      final orders = app.backend.orders;
      expect(find.text('Laura Meier'), findsNWidgets(2));
      String subtitle(Object id) =>
          tester.widget<Text>(find.byKey(Key('history-subtitle-$id'))).data!;
      expect(subtitle(orders[0]['id']!), contains('not invoiced'));
      expect(subtitle(orders[1]['id']!), contains('2026-0001 · open'));
    });

    testWidgets('issues the invoice of a forgotten order from the history', (tester) async {
      final (app, facial, laura) = await start(tester);
      await pickLaura(tester, laura);
      await addFacial(tester, facial);
      await tester.tap(find.byKey(const Key('order-confirm-only')));
      await tester.pumpAndSettle();

      await openHistory(tester);
      await tester.tap(find.text('Laura Meier'));
      await tester.pumpAndSettle();
      expect(find.text('Not invoiced'), findsOneWidget);
      await tester.tap(find.byKey(const Key('order-issue-invoice')));
      await tester.pumpAndSettle();
      expect(app.backend.salon.invoices.single['orderId'], app.backend.orders.single['id']);
      expect(find.text('Invoice 2026-0001 · open'), findsOneWidget);
      expect(find.byKey(const Key('order-open-invoice')), findsOneWidget);
    });
  });
}
