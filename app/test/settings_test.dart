import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/app_harness.dart';

/// Anna is staff, Boss an admin, Station Bar a station account.
Future<AppHarness> start(WidgetTester tester, {required String user, bool salon = false}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('boss', 'admin', '1111', displayName: 'Boss');
  b.addItem(b.addCategory('Drinks'), 'Cola', 'drink', '4.50');
  if (salon) b.salon.useSalon();
  await app.start();
  await app.login(user, user == 'boss' ? '1111' : '4711');
  return app;
}

Finder navLabel(String label) => find.descendant(
  of: find.byType(NavigationRail),
  matching: find.text(label),
);

Future<void> openSettings(WidgetTester tester) async {
  await tester.tap(navLabel('Settings'));
  await tester.pumpAndSettle();
}

Future<void> scrollTo(WidgetTester tester, Key key, {bool up = false}) async {
  await tester.scrollUntilVisible(
    find.byKey(key),
    up ? -200 : 200,
    scrollable: find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
}

void main() {
  group('navigation per mode', () {
    testWidgets('event mode shows tables and the menu, no salon functions', (tester) async {
      await start(tester, user: 'boss');
      expect(navLabel('Tables'), findsOneWidget);
      expect(navLabel('Menu'), findsOneWidget);
      expect(navLabel('Menu setup'), findsOneWidget);
      expect(navLabel('Customers'), findsNothing);
      expect(navLabel('Invoices'), findsNothing);
      expect(navLabel('Report'), findsNothing);
    });

    testWidgets('salon mode hides tables and uses salon terms', (tester) async {
      await start(tester, user: 'boss', salon: true);
      expect(navLabel('Tables'), findsNothing);
      expect(navLabel('Services'), findsOneWidget);
      expect(navLabel('Services setup'), findsOneWidget);
      expect(navLabel('Menu'), findsNothing);
      expect(navLabel('Customers'), findsOneWidget);
      expect(navLabel('Invoices'), findsOneWidget);
      expect(navLabel('Report'), findsOneWidget);
    });

    testWidgets('staff do not see settings or the report', (tester) async {
      await start(tester, user: 'anna', salon: true);
      expect(navLabel('Settings'), findsNothing);
      expect(navLabel('Report'), findsNothing);
      expect(navLabel('Customers'), findsOneWidget);
    });

    testWidgets('settings are loaded again when the app resumes', (tester) async {
      final app = await start(tester, user: 'boss');
      expect(navLabel('Tables'), findsOneWidget);
      app.backend.salon.useSalon();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(navLabel('Tables'), findsNothing);
      expect(navLabel('Services'), findsOneWidget);
    });
  });

  group('settings screen', () {
    testWidgets('admin switches to salon mode while there are no orders', (tester) async {
      final app = await start(tester, user: 'boss');
      await openSettings(tester);
      await tester.tap(find.text('Salon'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('mode-confirm')));
      await tester.pumpAndSettle();
      expect(app.backend.salon.isSalon, isTrue);
      expect(navLabel('Tables'), findsNothing);
    });

    testWidgets('mode change is refused once orders exist', (tester) async {
      final app = await start(tester, user: 'boss');
      app.backend.addSentOrder('12', app.backend.staff.first, const []);
      await openSettings(tester);
      await tester.tap(find.text('Salon'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('mode-confirm')));
      await tester.pumpAndSettle();
      expect(
        find.text('The installation already has orders, so its mode can no longer change'),
        findsOneWidget,
      );
      expect(app.backend.salon.isSalon, isFalse);
    });

    testWidgets('saves business details and shows server validation errors', (tester) async {
      final app = await start(tester, user: 'boss', salon: true);
      await openSettings(tester);
      await tester.enterText(find.byKey(const Key('settings-street')), '');
      await scrollTo(tester, const Key('settings-vat'));
      await tester.tap(find.byKey(const Key('settings-vat')));
      await tester.pumpAndSettle();
      await scrollTo(tester, const Key('settings-save'));
      await tester.tap(find.byKey(const Key('settings-save')));
      await tester.pumpAndSettle();
      expect(find.text('Required in salon mode'), findsOneWidget);
      expect(find.text('A VAT number is required when VAT is turned on'), findsOneWidget);

      await scrollTo(tester, const Key('settings-vatUid'));
      await tester.enterText(find.byKey(const Key('settings-vatUid')), 'CHE-116.281.710 MWST');
      await scrollTo(tester, const Key('settings-street'), up: true);
      await tester.enterText(find.byKey(const Key('settings-street')), 'Marktgasse 9');
      await scrollTo(tester, const Key('settings-save'));
      await tester.tap(find.byKey(const Key('settings-save')));
      await tester.pumpAndSettle();
      expect(find.text('Settings saved'), findsOneWidget);
      expect(app.backend.salon.settings['street'], 'Marktgasse 9');
      expect(app.backend.salon.settings['vatEnabled'], isTrue);
    });
  });

  testWidgets('salon staff administration says "staff" and offers no station role', (tester) async {
    await start(tester, user: 'boss', salon: true);
    await tester.tap(navLabel('Staff'));
    await tester.pumpAndSettle();
    expect(find.text('anna · staff'), findsOneWidget);
    await tester.tap(find.text('Anna'));
    await tester.pumpAndSettle();
    expect(find.descendant(of: find.byType(AlertDialog), matching: find.text('Staff')),
        findsOneWidget);
    expect(find.text('Station'), findsNothing);
  });
}
