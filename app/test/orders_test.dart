import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/orders/polling.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

class _Menu {
  late int drinks, cola, beer, pizza;
}

/// Logs in as Anna on the Orders tab, with Cola, Beer, and Margherita on the menu.
Future<(AppHarness, _Menu)> start(WidgetTester tester, {Size size = const Size(1280, 900)}) async {
  final app = AppHarness(tester);
  final menu = _Menu();
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  menu.drinks = b.addCategory('Drinks');
  menu.cola = b.addItem(menu.drinks, 'Cola', 'drink', '4.50');
  menu.beer = b.addItem(menu.drinks, 'Beer', 'drink', '6.00');
  menu.pizza = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00');
  await app.start();
  if (size != const Size(1280, 900)) tester.view.physicalSize = size;
  await app.login('anna', '4711');
  return (app, menu);
}

Future<void> openTable(WidgetTester tester, String table) async {
  await tester.enterText(find.byKey(const Key('table-input')), table);
  await tester.tap(find.byKey(const Key('table-open')));
  await tester.pumpAndSettle();
}

Future<void> tapTile(WidgetTester tester, int itemId) async {
  await tester.tap(find.byKey(Key('menu-tile-$itemId')));
  await tester.pumpAndSettle();
}

Map<String, dynamic> lineOf(FakeBackend b, String name) => (b.orders.single['lines'] as List)
    .cast<Map<String, dynamic>>()
    .firstWhere((l) => l['name'] == name);

String total(WidgetTester tester) =>
    tester.widget<Text>(find.byKey(const Key('order-total'))).data!;

Finder sendButton() => find.byKey(const Key('order-send'));

bool sendEnabled(WidgetTester tester) => tester.widget<FilledButton>(sendButton()).enabled;

String sendLabel(WidgetTester tester) =>
    tester.widget<Text>(find.descendant(of: sendButton(), matching: find.byType(Text))).data!;

void main() {
  testWidgets('opening a table shows its order screen', (tester) async {
    final (app, _) = await start(tester);
    await openTable(tester, '12');

    expect(find.text('Table 12'), findsOneWidget);
    expect(find.text('Opened by Anna'), findsOneWidget);
    expect(app.backend.orders.single['tableLabel'], '12');
    expect(total(tester), 'CHF 0.00');
  });

  testWidgets('invalid table label is rejected in the app', (tester) async {
    final (app, _) = await start(tester);
    await openTable(tester, 'T-1');

    expect(find.text('1 to 10 letters or digits'), findsOneWidget);
    expect(app.backend.orders, isEmpty);
  });

  testWidgets('tapping tiles adds lines with a running total', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');

    await tapTile(tester, menu.cola);
    await tapTile(tester, menu.cola);
    await tapTile(tester, menu.pizza);

    expect(lineOf(app.backend, 'Cola')['qty'], 2);
    expect(lineOf(app.backend, 'Margherita')['qty'], 1);
    expect(total(tester), 'CHF 27.00');
    expect(sendLabel(tester), 'Send (2)');
  });

  testWidgets('quantity and note of an unsent line can be changed, and the line removed', (
    tester,
  ) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    await tapTile(tester, menu.pizza);
    final id = lineOf(app.backend, 'Margherita')['id'];

    await tester.tap(find.byKey(Key('line-inc-$id')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(Key('line-inc-$id')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(Key('line-dec-$id')));
    await tester.pumpAndSettle();
    expect(tester.widget<Text>(find.byKey(Key('line-qty-$id'))).data, '2');

    await tester.tap(find.byKey(Key('line-note-$id')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('dialog-text')), 'extra spicy');
    await tester.tap(find.byKey(const Key('dialog-submit')));
    await tester.pumpAndSettle();
    expect(find.text('extra spicy'), findsOneWidget);
    expect(lineOf(app.backend, 'Margherita')['note'], 'extra spicy');
    expect(total(tester), 'CHF 36.00');

    // A tile tap does not merge into a line with a note.
    await tapTile(tester, menu.pizza);
    expect((app.backend.orders.single['lines'] as List), hasLength(2));

    await tester.tap(find.byKey(Key('line-remove-$id')));
    await tester.pumpAndSettle();
    expect(find.text('extra spicy'), findsNothing);
    expect(total(tester), 'CHF 18.00');
  });

  testWidgets('send marks lines as sent and shows the round', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    expect(sendEnabled(tester), isFalse);

    await tapTile(tester, menu.cola);
    await tapTile(tester, menu.pizza);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(sendLabel(tester), 'Sent · round 1');
    expect(sendEnabled(tester), isFalse);
    expect(find.text('Sent · round 1'), findsNWidgets(3)); // two lines and the button
    expect(find.text('1 × Cola'), findsOneWidget);
    expect(lineOf(app.backend, 'Cola')['status'], 'sent');

    // A follow-up line makes the button available again.
    await tapTile(tester, menu.beer);
    expect(sendLabel(tester), 'Send (1)');
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    expect(sendLabel(tester), 'Sent · round 2');
  });

  testWidgets('adding, sending, and cancelling carry an idempotency key', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    await tapTile(tester, menu.cola);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(Key('line-cancel-${lineOf(app.backend, 'Cola')['id']}')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('dialog-text')), 'spilled');
    await tester.tap(find.byKey(const Key('dialog-submit')));
    await tester.pumpAndSettle();

    final writes = app.backend.orderWrites.where((w) => w.path != '/orders').toList();
    expect(writes.map((w) => w.path.split('/').last), ['lines', 'send', 'cancel']);
    final keys = writes.map((w) => w.key).toList();
    expect(
      keys,
      everyElement(
        matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')),
      ),
    );
    expect(keys.toSet(), hasLength(3));
  });

  testWidgets('a sent line is cancelled with a reason and no longer counts', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    await tapTile(tester, menu.cola);
    await tapTile(tester, menu.cola);
    await tapTile(tester, menu.pizza);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();
    final pizza = lineOf(app.backend, 'Margherita')['id'];

    await tester.tap(find.byKey(Key('line-cancel-$pizza')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('dialog-submit')));
    await tester.pumpAndSettle();
    expect(find.text('Reason is required'), findsOneWidget);

    await tester.enterText(find.byKey(const Key('dialog-text')), 'guest changed mind');
    await tester.tap(find.byKey(const Key('dialog-submit')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Cancelled: guest changed mind'), findsOneWidget);
    expect(total(tester), 'CHF 9.00');
    expect(find.byKey(Key('line-cancel-$pizza')), findsNothing);
    final title = tester.widget<Text>(find.text('1 × Margherita'));
    expect(title.style?.decoration, TextDecoration.lineThrough);
  });

  testWidgets('a deactivated item is rejected and the menu refreshed', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    app.backend.items.firstWhere((i) => i['id'] == menu.beer)['active'] = false;

    await tapTile(tester, menu.beer);

    expect(find.text('Beer is no longer available'), findsOneWidget);
    expect(find.byKey(Key('menu-tile-${menu.beer}')), findsNothing);
    expect(app.backend.orders.single['lines'], isEmpty);
  });

  testWidgets('overview lists open orders with table, waiter, total, and age', (tester) async {
    final (app, menu) = await start(tester);
    final ben = app.backend.addStaff('ben', 'waiter', '2222', displayName: 'Ben');
    final t5 = app.backend.openOrder('5', ben, ageMinutes: 14);
    app.backend.addOrderLine(t5['id'] as int, menu.pizza);
    await tester.pump(Polling.pollInterval);
    await tester.pumpAndSettle();

    expect(find.text('Table 5'), findsOneWidget);
    expect(find.text('Ben · 14 min · 1 not sent'), findsOneWidget);
    expect(find.text('CHF 18.00'), findsOneWidget);

    await tester.tap(find.text('Table 5'));
    await tester.pumpAndSettle();
    expect(find.text('Opened by Ben'), findsOneWidget);
    expect(total(tester), 'CHF 18.00');
  });

  testWidgets('changes from another device appear within one poll', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '5');
    final order = app.backend.orders.single['id'] as int;

    final beer = app.backend.addOrderLine(order, menu.beer, qty: 3);
    await tester.pump(const Duration(seconds: 1));
    expect(find.byKey(Key('line-${beer['id']}')), findsNothing);

    await tester.pump(Polling.pollInterval);
    await tester.pumpAndSettle();
    expect(find.byKey(Key('line-${beer['id']}')), findsOneWidget);
    expect(total(tester), 'CHF 18.00');
  });

  testWidgets('without a connection a banner shows and sending is blocked', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    await tapTile(tester, menu.cola);
    expect(sendEnabled(tester), isTrue);

    app.backend.offline = true;
    await tester.pump(Polling.pollInterval);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('no-connection')), findsOneWidget);
    expect(sendEnabled(tester), isFalse);

    app.backend.offline = false;
    await tester.pump(Polling.pollInterval);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('no-connection')), findsNothing);
    expect(sendEnabled(tester), isTrue);
  });

  testWidgets('a line changed on another device is not overwritten', (tester) async {
    final (app, menu) = await start(tester);
    await openTable(tester, '12');
    await tapTile(tester, menu.cola);
    final cola = lineOf(app.backend, 'Cola');
    // Another device sets the quantity to 5 before this one polls.
    cola
      ..['qty'] = 5
      ..['version'] = (cola['version'] as int) + 1;

    await tester.tap(find.byKey(Key('line-inc-${cola['id']}')));
    await tester.pumpAndSettle();

    expect(find.textContaining('changed on another device'), findsOneWidget);
    expect(cola['qty'], 5);
    expect(tester.widget<Text>(find.byKey(Key('line-qty-${cola['id']}'))).data, '5');
  });

  testWidgets('works on a phone-sized screen', (tester) async {
    final (app, menu) = await start(tester, size: const Size(400, 800));
    await openTable(tester, '3');
    await tapTile(tester, menu.cola);
    await tester.tap(sendButton());
    await tester.pumpAndSettle();

    expect(lineOf(app.backend, 'Cola')['status'], 'sent');
    expect(tester.takeException(), isNull);
  });
}
