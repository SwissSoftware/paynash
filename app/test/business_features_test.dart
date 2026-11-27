import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

/// Order screen and printer screen per business template (change
/// configurable-business-features; specs order-taking, ticket-printing).
Future<(AppHarness, int, int)> start(WidgetTester tester, String template, {bool admin = false}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.salon.useTemplate(template);
  final croissant = b.addItem(b.addCategory('Bakery'), 'Croissant', 'food', '2.80');
  final laura = b.salon.addCustomer('Laura', 'Meier');
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return (app, croissant, laura);
}

Finder sendButton() => find.byKey(const Key('order-send'));

String sendLabel(WidgetTester tester) => tester
    .widget<Text>(find.descendant(of: sendButton(), matching: find.byType(Text)).last)
    .data!;

Future<void> pickCustomer(WidgetTester tester, int id) async {
  await tester.tap(find.byKey(const Key('order-customer')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(Key('picker-customer-$id')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('counter sale needs neither table nor customer', (tester) async {
    final (app, croissant, _) = await start(tester, 'counter');
    expect(find.byKey(const Key('table-input')), findsNothing);
    expect(find.byKey(const Key('order-customer')), findsNothing);

    await tester.tap(find.byKey(Key('menu-tile-$croissant')));
    await tester.tap(find.byKey(Key('menu-tile-$croissant')));
    await tester.pump();
    expect(sendLabel(tester), 'Sell (2)');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    final order = app.backend.orders.single;
    expect(order['tableLabel'], isNull);
    expect(order['customerId'], isNull);
    expect(find.text('Order sold'), findsOneWidget);
  });

  testWidgets('restaurant sends a table order without customer', (tester) async {
    final (app, croissant, _) = await start(tester, 'restaurant');
    expect(find.text('Customer (optional)'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('table-input')), '12');
    await tester.tap(find.byKey(Key('menu-tile-$croissant')));
    await tester.pump();
    expect(sendLabel(tester), 'Send (1)');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(app.backend.orders.single['tableLabel'], '12');
    expect(app.backend.orders.single['customerId'], isNull);
    expect(app.backend.salon.invoices, isEmpty);
  });

  testWidgets('restaurant invoices a table order for a company customer', (tester) async {
    final (app, croissant, laura) = await start(tester, 'restaurant');
    app.backend.salon.useSalon();
    app.backend.salon.useTemplate('restaurant');
    await tester.enterText(find.byKey(const Key('table-input')), '12');
    await pickCustomer(tester, laura);
    await tester.tap(find.byKey(Key('menu-tile-$croissant')));
    await tester.pump();
    expect(sendLabel(tester), 'Send and invoice (1)');
    expect(find.byKey(const Key('order-confirm-only')), findsOneWidget);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    final order = app.backend.orders.single;
    expect(order['tableLabel'], '12');
    expect(order['customerId'], laura);
    expect(app.backend.salon.invoices, hasLength(1));
  });

  testWidgets('the receipts switch is hidden while invoices are off', (tester) async {
    await start(tester, 'event', admin: true);
    await tester.tap(find.descendant(of: find.byType(NavigationRail), matching: find.text('Printers')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('printer-add')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('printer-tickets')), findsOneWidget);
    expect(find.byKey(const Key('printer-receipts')), findsNothing);
  });
}
