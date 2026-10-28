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
}
