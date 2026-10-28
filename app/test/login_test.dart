import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

void main() {
  testWidgets('waiter logs in, sees name, and has no admin sections',
      (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    await app.start();

    await app.login('anna', '4711');

    expect(app.currentUser('Anna'), findsOneWidget);
    expect(find.byKey(const Key('table-input')), findsOneWidget);
    expect(find.text('Staff'), findsNothing);
    expect(app.tokens.token, isNotNull);
  });

  testWidgets('wrong PIN shows the generic error and stays on login',
      (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('anna', 'waiter', '4711');
    await app.start();

    await app.login('anna', '0000');

    expect(find.text('Invalid credentials'), findsOneWidget);
    expect(app.loginScreen, findsOneWidget);
    expect(app.tokens.token, isNull);
  });

  testWidgets('logout ends the session on the server and locally',
      (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    await app.start();
    await app.login('anna', '4711');

    await app.logout();

    expect(app.loginScreen, findsOneWidget);
    expect(app.tokens.token, isNull);
    expect(app.backend.sessions, isEmpty);
  });

  testWidgets('a 401 from the server returns to the login screen',
      (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('boss', 'admin', '1111', displayName: 'Boss');
    await app.start();
    await app.login('boss', '1111');

    app.backend.expireAllSessions();
    await tester.tap(find.text('Staff'));
    await tester.pumpAndSettle();

    expect(app.loginScreen, findsOneWidget);
    expect(find.text('Your session has ended. Please log in again.'),
        findsOneWidget);
    expect(app.tokens.token, isNull);
  });

  testWidgets('a saved valid session is restored on start', (tester) async {
    final app = AppHarness(tester);
    app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
    app.backend.sessions['saved'] = app.backend.staff.first;
    app.tokens.token = 'saved';

    await app.start();

    expect(app.currentUser('Anna'), findsOneWidget);
  });

  testWidgets('a saved expired session leads to the login screen',
      (tester) async {
    final app = AppHarness(tester);
    app.tokens.token = 'stale';

    await app.start();

    expect(app.loginScreen, findsOneWidget);
    expect(app.tokens.token, isNull);
  });
}
