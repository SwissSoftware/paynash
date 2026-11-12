import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

Future<void> openStaff(WidgetTester tester) async {
  await tester.tap(find.text('Staff'));
  await tester.pumpAndSettle();
}

Future<void> addStaff(WidgetTester tester,
    {required String username, required String name, required String pin}) async {
  await tester.tap(find.byKey(const Key('staff-add')));
  await tester.pumpAndSettle();
  await tester.enterText(find.byKey(const Key('staff-username')), username);
  await tester.enterText(find.byKey(const Key('staff-display-name')), name);
  await tester.enterText(find.byKey(const Key('staff-pin')), pin);
  expect(
      tester
          .widget<EditableText>(find.descendant(
              of: find.byKey(const Key('staff-pin')),
              matching: find.byType(EditableText)))
          .obscureText,
      isFalse,
      reason: 'admin-set PINs are shown in plain text');
  await tester.tap(find.byKey(const Key('staff-save')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('admin creates a waiter who can then log in', (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('boss', 'admin', '1111', displayName: 'Boss');
    await app.start();
    await app.login('boss', '1111');
    await openStaff(tester);

    await addStaff(tester, username: 'anna', name: 'Anna', pin: '4711');

    expect(find.byKey(const Key('staff-anna')), findsOneWidget);
    await app.logout();
    await app.login('anna', '4711');
    expect(app.currentUser('Anna'), findsOneWidget);
  });

  testWidgets('duplicate username shows the field error', (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('boss', 'admin', '1111');
    app.backend.addStaff('anna', 'waiter', '4711');
    await app.start();
    await app.login('boss', '1111');
    await openStaff(tester);

    await addStaff(tester, username: 'ANNA', name: 'Other', pin: '1234');

    expect(find.text('Username already exists'), findsOneWidget);
    expect(app.backend.staff, hasLength(2));
  });

  testWidgets('admin deactivates a waiter, who can no longer log in',
      (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('boss', 'admin', '1111');
    app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    await app.start();
    await app.login('boss', '1111');
    await openStaff(tester);

    await tester.tap(find.byKey(const Key('staff-anna')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('staff-active')));
    await tester.tap(find.byKey(const Key('staff-save')));
    await tester.pumpAndSettle();

    expect(find.text('anna · waiter · inactive'), findsOneWidget);
    await app.logout();
    await app.login('anna', '4711');
    expect(find.text('Invalid credentials'), findsOneWidget);
  });

  testWidgets('admin resets a PIN', (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('boss', 'admin', '1111');
    app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    await app.start();
    await app.login('boss', '1111');
    await openStaff(tester);

    await tester.tap(find.descendant(
        of: find.byKey(const Key('staff-anna')),
        matching: find.byTooltip('Reset PIN')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('reset-pin')), '8642');
    await tester.tap(find.byKey(const Key('reset-pin-save')));
    await tester.pumpAndSettle();

    expect(find.text('PIN for Anna was reset'), findsOneWidget);
    expect(app.backend.staff.last.pin, '8642');
  });

  group('stations', () {
    Future<AppHarness> startAsAdmin(WidgetTester tester) async {
      final app = AppHarness(tester);
      app.backend.addStaff('boss', 'admin', '1111', displayName: 'Boss');
      app.backend.addPrinter('Bar', 'drink');
      app.backend.addPrinter('Kitchen', 'food', address: '192.168.1.51');
      await app.start();
      await app.login('boss', '1111');
      await openStaff(tester);
      return app;
    }

    Future<void> fillStation(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('staff-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('staff-username')), 'station-bar');
      await tester.enterText(find.byKey(const Key('staff-display-name')), 'Station Bar');
      await tester.enterText(find.byKey(const Key('staff-pin')), '5555');
      await tester.tap(find.text('Station'));
      await tester.pumpAndSettle();
    }

    testWidgets('admin creates a station for the bar printer, which then sees the station view', (
      tester,
    ) async {
      final app = await startAsAdmin(tester);
      await fillStation(tester);

      await tester.tap(find.byKey(const Key('staff-printer-Bar')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('staff-save')));
      await tester.pumpAndSettle();

      final station = app.backend.staff.last;
      expect(station.role, 'station');
      expect(station.printerIds, [app.backend.printers.first['id']]);
      final icon = tester.widget<Icon>(
        find
            .descendant(of: find.byKey(const Key('staff-station-bar')), matching: find.byType(Icon))
            .first,
      );
      expect(icon.icon, Icons.print);

      await app.logout();
      await app.login('station-bar', '5555');
      expect(find.byKey(const Key('station-arm')), findsOneWidget);
      expect(find.text('Staff'), findsNothing);
    });

    testWidgets('a station without a printer shows the error', (tester) async {
      final app = await startAsAdmin(tester);
      await fillStation(tester);

      await tester.tap(find.byKey(const Key('staff-save')));
      await tester.pumpAndSettle();

      expect(find.text('A station needs at least one printer'), findsOneWidget);
      expect(app.backend.staff, hasLength(1));
    });

    testWidgets('printer choices are only shown for stations', (tester) async {
      await startAsAdmin(tester);
      await tester.tap(find.byKey(const Key('staff-add')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('staff-printer-Bar')), findsNothing);
      await tester.tap(find.text('Station'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('staff-printer-Bar')), findsOneWidget);
      expect(find.byKey(const Key('staff-printer-Kitchen')), findsOneWidget);
    });
  });
}
