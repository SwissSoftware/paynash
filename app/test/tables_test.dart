import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/orders/polling.dart';

import 'support/app_harness.dart';

/// Logs in with a Cola on the menu; Anna is a waiter, Boss an admin.
Future<(AppHarness, int)> start(WidgetTester tester,
    {bool admin = false, Size? size}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  final cola = b.addItem(b.addCategory('Drinks'), 'Cola', 'drink', '4.50');
  await app.start();
  if (size != null) tester.view.physicalSize = size;
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  return (app, cola);
}

Future<void> openTables(WidgetTester tester) async {
  await tester.tap(find.text('Tables'));
  await tester.pumpAndSettle();
}

Future<void> saveTable(WidgetTester tester, {String? number, String? name}) async {
  if (number != null) {
    await tester.enterText(find.byKey(const Key('table-number')), number);
  }
  if (name != null) await tester.enterText(find.byKey(const Key('table-name')), name);
  await tester.tap(find.byKey(const Key('table-save')));
  await tester.pumpAndSettle();
}

Future<void> addTable(WidgetTester tester, String number, [String? name]) async {
  await tester.tap(find.byKey(const Key('table-add')));
  await tester.pumpAndSettle();
  await saveTable(tester, number: number, name: name);
}

Finder chip(String number) => find.byKey(Key('table-chip-$number'));

bool chipSelected(WidgetTester tester, String number) =>
    tester.widget<ChoiceChip>(chip(number)).selected;

String tableText(WidgetTester tester) =>
    tester.widget<TextField>(find.byKey(const Key('table-input'))).controller!.text;

Future<void> sendCola(WidgetTester tester, int cola) async {
  await tester.tap(find.byKey(Key('menu-tile-$cola')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('order-send')));
  await tester.pumpAndSettle();
}

void main() {
  group('admin', () {
    testWidgets('creates tables with and without a name', (tester) async {
      final (app, _) = await start(tester, admin: true);
      await openTables(tester);

      await addTable(tester, '12', 'Terrace window');
      await addTable(tester, 't5');

      expect(find.byKey(const Key('table-12')), findsOneWidget);
      expect(find.text('Terrace window'), findsOneWidget);
      expect(find.byKey(const Key('table-T5')), findsOneWidget);
      expect(app.backend.tables.map((t) => (t['number'], t['name'])),
          [('12', 'Terrace window'), ('T5', null)]);
    });

    testWidgets('a duplicate number shows the field error', (tester) async {
      final (app, _) = await start(tester, admin: true);
      app.backend.addTable('12');
      await openTables(tester);

      await addTable(tester, '12');

      expect(find.text('This number is already in use'), findsOneWidget);
      expect(app.backend.tables, hasLength(1));
    });

    testWidgets('deactivating marks the table and hides it from waiters', (tester) async {
      final (app, _) = await start(tester, admin: true);
      app.backend.addTable('T1');
      await openTables(tester);

      await tester.tap(find.byKey(const Key('table-T1')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('table-active')));
      await saveTable(tester);

      expect(app.backend.tables.single['active'], isFalse);
      expect(find.text('inactive'), findsOneWidget);
      await tester.tap(find.text('New order'));
      await tester.pumpAndSettle();
      expect(chip('T1'), findsNothing);
    });

    testWidgets('deletes a table only after confirming', (tester) async {
      final (app, _) = await start(tester, admin: true);
      app.backend.addTable('8');
      await openTables(tester);

      await tester.tap(find.byKey(const Key('table-delete-8')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(app.backend.tables, hasLength(1));

      await tester.tap(find.byKey(const Key('table-delete-8')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('table-delete-confirm')));
      await tester.pumpAndSettle();

      expect(app.backend.tables, isEmpty);
      expect(find.byKey(const Key('table-8')), findsNothing);
    });

    testWidgets('waiters do not see the tables section', (tester) async {
      await start(tester);
      expect(find.text('Tables'), findsNothing);
    });
  });

  group('picker', () {
    testWidgets('picking a table sends the order with its number', (tester) async {
      final (app, cola) = await start(tester);
      app.backend
        ..addTable('10')
        ..addTable('2', name: 'Window')
        ..addTable('3', active: false);
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      expect(find.text('Window'), findsOneWidget);
      expect(chip('3'), findsNothing);
      expect(tester.getTopLeft(chip('2')).dx, lessThan(tester.getTopLeft(chip('10')).dx));

      await tester.tap(chip('2'));
      await tester.pumpAndSettle();
      expect(tableText(tester), '2');
      expect(chipSelected(tester, '2'), isTrue);

      await sendCola(tester, cola);

      expect(app.backend.orders.single['tableLabel'], '2');
      expect(chipSelected(tester, '2'), isFalse);
    });

    testWidgets('typing a configured number selects its chip', (tester) async {
      final (app, _) = await start(tester);
      app.backend.addTable('T12');
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('table-input')), 't12');
      await tester.pump();

      expect(chipSelected(tester, 'T12'), isTrue);
    });

    testWidgets('a typed label without a table is sent unchanged', (tester) async {
      final (app, cola) = await start(tester);
      app.backend.addTable('1');
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('table-input')), '99');
      await sendCola(tester, cola);

      expect(app.backend.orders.single['tableLabel'], '99');
    });

    testWidgets('without tables only the field is shown', (tester) async {
      final (app, cola) = await start(tester);
      expect(find.byWidgetPredicate((w) => '${w.key}'.contains('table-chip-')), findsNothing);

      await tester.enterText(find.byKey(const Key('table-input')), '4');
      await sendCola(tester, cola);

      expect(app.backend.orders.single['tableLabel'], '4');
    });

    testWidgets('a table deactivated meanwhile disappears on the next poll', (tester) async {
      final (app, _) = await start(tester);
      final id = app.backend.addTable('5');
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();
      expect(chip('5'), findsOneWidget);

      app.backend.tables.firstWhere((t) => t['id'] == id)['active'] = false;
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      expect(chip('5'), findsNothing);
    });

    testWidgets('many tables fit on a phone-sized screen', (tester) async {
      final (app, cola) = await start(tester, size: const Size(400, 800));
      for (var i = 1; i <= 40; i++) {
        app.backend.addTable('$i');
      }
      await tester.pump(Polling.pollInterval);
      await tester.pumpAndSettle();

      await tester.tap(chip('1'));
      await tester.pumpAndSettle();
      await sendCola(tester, cola);

      expect(app.backend.orders.single['tableLabel'], '1');
      expect(tester.takeException(), isNull);
    });
  });
}
