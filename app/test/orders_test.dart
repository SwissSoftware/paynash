import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/orders/polling.dart';

import 'support/app_harness.dart';

class _Menu {
  late int drinks, cola, beer, pizza;
}

/// Logs in as Anna (or as an admin) on the New order tab, with Cola, Beer, and
/// Margherita on the menu.
Future<(AppHarness, _Menu)> start(
  WidgetTester tester, {
  Size size = const Size(1280, 900),
  bool admin = false,
}) async {
  final app = AppHarness(tester);
  final menu = _Menu();
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  menu.drinks = b.addCategory('Drinks');
  menu.cola = b.addItem(menu.drinks, 'Cola', 'drink', '4.50');
  menu.beer = b.addItem(menu.drinks, 'Beer', 'drink', '6.00');
  menu.pizza = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00');
  await app.start();
  if (size != const Size(1280, 900)) tester.view.physicalSize = size;
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return (app, menu);
}

Future<void> enterTable(WidgetTester tester, String table) async {
  await tester.enterText(find.byKey(const Key('table-input')), table);
  await tester.pump();
}

Future<void> tapTile(WidgetTester tester, int itemId) async {
  await tester.tap(find.byKey(Key('menu-tile-$itemId')));
  await tester.pumpAndSettle();
}

Future<void> send(WidgetTester tester) async {
  await tester.tap(sendButton());
  await tester.pumpAndSettle();
}

Future<void> openHistory(WidgetTester tester) async {
  await tester.tap(find.text('History'));
  await tester.pumpAndSettle();
}

Future<void> enterReason(WidgetTester tester, String reason) async {
  await tester.enterText(find.byKey(const Key('dialog-text')), reason);
  await tester.tap(find.byKey(const Key('dialog-submit')));
  await tester.pumpAndSettle();
}

Map<String, dynamic> lineOf(Map<String, dynamic> order, String name) =>
    (order['lines'] as List).cast<Map<String, dynamic>>().firstWhere((l) => l['name'] == name);

String total(WidgetTester tester) =>
    tester.widget<Text>(find.byKey(const Key('order-total'))).data!;

Finder sendButton() => find.byKey(const Key('order-send'));

bool sendEnabled(WidgetTester tester) => tester.widget<FilledButton>(sendButton()).enabled;

String sendLabel(WidgetTester tester) =>
    tester.widget<Text>(find.descendant(of: sendButton(), matching: find.byType(Text))).data!;

void main() {
  group('new order', () {
    testWidgets('tapping tiles builds the draft on the device with a running total', (
      tester,
    ) async {
      final (app, menu) = await start(tester);
      expect(sendEnabled(tester), isFalse);

      await tapTile(tester, menu.cola);
      await tapTile(tester, menu.cola);
      await tapTile(tester, menu.pizza);

      expect(tester.widget<Text>(find.byKey(const Key('line-qty-0'))).data, '2');
      expect(total(tester), 'CHF 27.00');
      expect(sendLabel(tester), 'Send (3)');
      expect(app.backend.orders, isEmpty, reason: 'nothing is stored before sending');
    });

    testWidgets('quantity and note can be changed and a line removed', (tester) async {
      final (_, menu) = await start(tester);
      await tapTile(tester, menu.pizza);

      await tester.tap(find.byKey(const Key('line-inc-0')));
      await tester.pump();
      await tester.tap(find.byKey(const Key('line-inc-0')));
      await tester.pump();
      await tester.tap(find.byKey(const Key('line-dec-0')));
      await tester.pump();
      expect(tester.widget<Text>(find.byKey(const Key('line-qty-0'))).data, '2');

      await tester.tap(find.byKey(const Key('line-note-0')));
      await tester.pumpAndSettle();
      await enterReason(tester, 'extra spicy');
      expect(find.text('extra spicy'), findsOneWidget);
      expect(total(tester), 'CHF 36.00');

      // A tile tap does not merge into a line with a note.
      await tapTile(tester, menu.pizza);
      expect(find.byKey(const Key('draft-line-1')), findsOneWidget);

      await tester.tap(find.byKey(const Key('line-remove-0')));
      await tester.pump();
      expect(find.text('extra spicy'), findsNothing);
      expect(total(tester), 'CHF 18.00');
    });

    testWidgets('sending saves the whole order and empties the screen', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, 't12');
      await tapTile(tester, menu.cola);
      await tapTile(tester, menu.pizza);
      await tester.tap(find.byKey(const Key('line-note-1')));
      await tester.pumpAndSettle();
      await enterReason(tester, 'extra spicy');

      await send(tester);

      final order = app.backend.orders.single;
      expect(order['tableLabel'], 'T12');
      expect(order['status'], 'sent');
      expect(lineOf(order, 'Cola')['qty'], 1);
      expect(lineOf(order, 'Margherita')['note'], 'extra spicy');
      expect(find.text('Order for table T12 sent'), findsOneWidget);
      expect(find.byKey(const Key('draft-line-0')), findsNothing);
      expect(tester.widget<TextField>(find.byKey(const Key('table-input'))).controller!.text, '');
      expect(total(tester), 'CHF 0.00');
    });

    testWidgets('a follow-up for the same table is a new order', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      await send(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.beer);
      await send(tester);

      expect(app.backend.orders, hasLength(2));
      expect((app.backend.orders.last['lines'] as List).single['name'], 'Beer');
    });

    testWidgets('the table is required and checked in the app', (tester) async {
      final (app, menu) = await start(tester);
      await tapTile(tester, menu.cola);

      await send(tester);
      expect(find.text('1 to 10 letters or digits'), findsOneWidget);

      await enterTable(tester, 'T-1');
      await send(tester);
      expect(find.text('1 to 10 letters or digits'), findsOneWidget);
      expect(app.backend.orders, isEmpty);
      expect(find.byKey(const Key('draft-line-0')), findsOneWidget);
    });

    testWidgets('a lost answer is retried with the same key and the order saved once', (
      tester,
    ) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      app.backend.loseSendAnswers = 1;

      await send(tester);

      expect(find.text('Order for table 12 sent'), findsOneWidget);
      expect(app.backend.orders, hasLength(1));
      final keys = app.backend.orderWrites.map((w) => w.key).toList();
      expect(keys, hasLength(2));
      expect(keys.toSet(), hasLength(1));
      expect(
        keys.first,
        matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')),
      );
    });

    testWidgets('a send that fails keeps the draft, and sending again uses the same key', (
      tester,
    ) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      app.backend.loseSendAnswers = 2;

      await send(tester);
      expect(find.text('Not sent: no connection. Please try again.'), findsOneWidget);
      expect(find.byKey(const Key('draft-line-0')), findsOneWidget);
      expect(find.byKey(const Key('no-connection')), findsOneWidget);
      expect(sendEnabled(tester), isFalse);

      // The next poll finds the server again. The order had reached it, so
      // sending again is answered from the first request.
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();
      await send(tester);

      expect(app.backend.orders, hasLength(1));
      expect(find.byKey(const Key('draft-line-0')), findsNothing);
      expect(app.backend.orderWrites.map((w) => w.key).toSet(), hasLength(1));
    });

    testWidgets('changing the draft after a failed send uses a new key', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      app.backend.loseSendAnswers = 2;
      await send(tester);
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      await tapTile(tester, menu.beer);
      await send(tester);

      expect(app.backend.orderWrites.map((w) => w.key).toSet(), hasLength(2));
      expect((app.backend.orders.last['lines'] as List).map((l) => l['name']), ['Cola', 'Beer']);
    });

    testWidgets('changing the table after a failed send uses a new key', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      app.backend.loseSendAnswers = 2;
      await send(tester);
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      await enterTable(tester, '14');
      await send(tester);

      expect(app.backend.orderWrites.map((w) => w.key).toSet(), hasLength(2));
      expect(app.backend.orders.map((o) => o['tableLabel']), ['12', '14']);
      expect(find.text('Order for table 14 sent'), findsOneWidget);
    });

    testWidgets('a deactivated item is removed from the draft and the rest kept', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
      await tapTile(tester, menu.cola);
      await tapTile(tester, menu.beer);
      app.backend.items.firstWhere((i) => i['id'] == menu.beer)['active'] = false;

      await send(tester);

      expect(find.text('Beer no longer available'), findsOneWidget);
      expect(app.backend.orders, isEmpty);
      expect(find.byKey(Key('menu-tile-${menu.beer}')), findsNothing);
      expect(find.byKey(const Key('draft-line-0')), findsOneWidget);
      expect(find.byKey(const Key('draft-line-1')), findsNothing);
      expect(total(tester), 'CHF 4.50');
    });

    testWidgets('without a connection a banner shows and sending is blocked', (tester) async {
      final (app, menu) = await start(tester);
      await enterTable(tester, '12');
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

    testWidgets('works on a phone-sized screen', (tester) async {
      final (app, menu) = await start(tester, size: const Size(400, 800));
      await enterTable(tester, '3');
      await tapTile(tester, menu.cola);
      await send(tester);

      expect(app.backend.orders.single['status'], 'sent');
      expect(tester.takeException(), isNull);
    });
  });

  group('history', () {
    testWidgets('lists sent orders newest first with time, waiter, total, and state', (
      tester,
    ) async {
      final (app, menu) = await start(tester);
      final ben = app.backend.staff.firstWhere((s) => s.username == 'boss');
      app.backend.addSentOrder('12', ben, [(menu.cola, 2)], sentAt: '2026-10-29T18:30:00Z');
      final voided = app.backend.addSentOrder('5', ben, [(menu.pizza, 1)]);
      voided
        ..['status'] = 'voided'
        ..['voidReason'] = 'test';
      await openHistory(tester);

      final tiles = tester.widgetList<ListTile>(find.byType(ListTile)).toList();
      expect((tiles[0].title as Text).data, 'Table 5');
      expect((tiles[1].title as Text).data, 'Table 12');
      expect(find.textContaining('Boss · Voided'), findsOneWidget);
      expect(find.text('CHF 9.00'), findsOneWidget);
    });

    testWidgets('an order sent on another device appears within one poll', (tester) async {
      final (app, menu) = await start(tester);
      await openHistory(tester);
      expect(find.text('No orders sent on this day.'), findsOneWidget);

      app.backend.addSentOrder('7', app.backend.staff.first, [(menu.beer, 1)]);
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();
      expect(find.text('Table 7'), findsOneWidget);
    });

    testWidgets('another day can be selected', (tester) async {
      final (app, menu) = await start(tester);
      final now = DateTime.now();
      final yesterday = DateTime(now.year, now.month, now.day - 1);
      final date =
          '${yesterday.year}-${yesterday.month.toString().padLeft(2, '0')}-'
          '${yesterday.day.toString().padLeft(2, '0')}';
      app.backend.addSentOrder('9', app.backend.staff.first, [
        (menu.cola, 1),
      ], sentAt: '${date}T12:00:00Z');
      await openHistory(tester);

      await tester.tap(find.byKey(const Key('history-prev-day')));
      await tester.pumpAndSettle();

      expect(find.text('Table 9'), findsOneWidget);
      expect(find.text('Today'), findsNothing);
      await tester.tap(find.byKey(const Key('history-next-day')));
      await tester.pumpAndSettle();
      expect(find.text('Today'), findsOneWidget);
    });

    testWidgets('waiters see the order lines but cannot cancel or void', (tester) async {
      final (app, menu) = await start(tester);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [
        (menu.cola, 2),
        (menu.pizza, 1),
      ]);
      await openHistory(tester);
      await tester.tap(find.text('Table 12'));
      await tester.pumpAndSettle();

      expect(find.text('Table 12 · #${order['id']}'), findsOneWidget);
      expect(find.text('2 × Cola'), findsOneWidget);
      expect(total(tester), 'CHF 27.00');
      expect(find.byKey(Key('line-cancel-${lineOf(order, 'Cola')['id']}')), findsNothing);
      expect(find.byKey(const Key('order-void')), findsNothing);
    });

    testWidgets('an admin cancels a line with a reason', (tester) async {
      final (app, menu) = await start(tester, admin: true);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [
        (menu.cola, 2),
        (menu.pizza, 1),
      ]);
      final pizza = lineOf(order, 'Margherita')['id'];
      await openHistory(tester);
      await tester.tap(find.text('Table 12'));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(Key('line-cancel-$pizza')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('dialog-submit')));
      await tester.pumpAndSettle();
      expect(find.text('Reason is required'), findsOneWidget);
      await enterReason(tester, 'guest changed mind');

      expect(find.textContaining('Cancelled: guest changed mind'), findsOneWidget);
      expect(total(tester), 'CHF 9.00');
      expect(find.byKey(Key('line-cancel-$pizza')), findsNothing);
      expect(
        tester.widget<Text>(find.text('1 × Margherita')).style?.decoration,
        TextDecoration.lineThrough,
      );
      final write = app.backend.orderWrites.single;
      expect(write.path, '/admin/orders/${order['id']}/lines/$pizza/cancel');
      expect(write.key, isNotNull);
    });

    testWidgets('an admin voids an order', (tester) async {
      final (app, menu) = await start(tester, admin: true);
      final order = app.backend.addSentOrder('12', app.backend.staff.first, [(menu.cola, 2)]);
      await openHistory(tester);
      await tester.tap(find.text('Table 12'));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('order-void')));
      await tester.pumpAndSettle();
      await enterReason(tester, 'wrong table');

      expect(order['status'], 'voided');
      expect(find.text('Voided: wrong table'), findsOneWidget);
      expect(total(tester), 'CHF 0.00');
      expect(find.byKey(const Key('order-void')), findsNothing);

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.textContaining('Voided'), findsOneWidget);
    });
  });
}
