import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/station/station_screen.dart';

import 'support/app_harness.dart';

/// A bar station with one printer, Anna as a waiter, and Beer and Margherita
/// on the menu.
Future<(AppHarness, Map<String, dynamic>, Map<String, dynamic>)> start(
  WidgetTester tester, {
  String status = 'ready',
  int waiting = 0,
  int unclear = 0,
  int? oldestWaitingSeconds,
}) async {
  final app = AppHarness(tester);
  final b = app.backend;
  final anna = b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  b.addStaff('station-bar', 'station', '5555', displayName: 'Station Bar');
  final beer = b.addItem(b.addCategory('Drinks'), 'Beer', 'drink', '6.00');
  final pizza = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00');
  final bar = b.addStationPrinter(
    'Bar',
    status: status,
    waiting: waiting,
    unclear: unclear,
    lastPrintedLabel: 'BAR 0141',
    oldestWaitingSeconds: oldestWaitingSeconds,
  );
  final order = b.addSentOrder('12', anna, [(beer, 2), (pizza, 1)]);
  await app.start();
  await app.login('station-bar', '5555');
  return (app, bar, order);
}

Future<void> nextPoll(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 2));
  await tester.pumpAndSettle();
}

Finder alert() => find.byKey(const Key('station-alert'));

void main() {
  group('ticket list', () {
    testWidgets('a station sees only its printer and tickets with their details', (tester) async {
      final app = AppHarness(tester);
      final b = app.backend;
      final anna = b.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
      b.addStaff('station-bar', 'station', '5555', displayName: 'Station Bar');
      final beer = b.addItem(b.addCategory('Drinks'), 'Beer', 'drink', '6.00');
      final bar = b.addStationPrinter('Bar', lastPrintedLabel: 'BAR 0141');
      final order = b.addSentOrder('12', anna, [(beer, 2)]);
      final ticket = b.addStationTicket(bar, order, label: 'BAR 0142', lines: 2);
      await app.start();
      await app.login('station-bar', '5555');

      expect(find.text('New order'), findsNothing);
      expect(find.text('History'), findsNothing);
      expect(find.text('Bar · ready · last BAR 0141'), findsOneWidget);
      expect(find.text('BAR 0142 · Table 12 · #${order['id']}'), findsOneWidget);
      expect(find.text('Anna · 2 lines · Printed'), findsOneWidget);
      expect(find.byKey(Key('station-reprint-${ticket['id']}')), findsOneWidget);
      expect(app.station.awake, isTrue, reason: 'the station keeps the display on');
      expect(alert(), findsNothing);
    });

    testWidgets('a new ticket appears with the next poll', (tester) async {
      final (app, bar, order) = await start(tester);
      expect(find.text('No tickets yet today.'), findsOneWidget);

      app.backend.addStationTicket(bar, order, label: 'BAR 0142', status: 'pending');
      await nextPoll(tester);

      expect(find.text('Anna · 1 line · Waiting'), findsOneWidget);
    });

    testWidgets('an unclear ticket is pinned on top and "was printed" resolves it', (tester) async {
      final (app, bar, order) = await start(tester);
      app.backend.addStationTicket(
        bar,
        order,
        label: 'BAR 0141',
        status: 'unclear',
        error: 'no answer',
      );
      final newer = app.backend.addStationTicket(bar, order, label: 'BAR 0142');
      await nextPoll(tester);
      final unclear = app.backend.stationTickets[bar['id']]!.last;

      final rows = tester.widgetList<ListTile>(find.byType(ListTile)).map((t) => t.key).toList();
      expect(rows.first, Key('station-ticket-${unclear['id']}'));
      expect(rows[1], Key('station-ticket-${newer['id']}'));
      expect(find.text('Anna · 1 line · UNCLEAR: no answer'), findsOneWidget);

      await tester.tap(find.byKey(Key('station-was-printed-${unclear['id']}')));
      await tester.pumpAndSettle();

      expect(app.backend.stationWrites.single.path, '/station/tickets/${unclear['id']}/resolve');
      expect(app.backend.stationWrites.single.body, {'resolution': 'printed'});
      expect(find.byKey(Key('station-was-printed-${unclear['id']}')), findsNothing);
    });

    testWidgets('"reprint" on an unclear ticket prints it again', (tester) async {
      final (app, bar, order) = await start(tester);
      final unclear = app.backend.addStationTicket(
        bar,
        order,
        label: 'BAR 0141',
        status: 'unclear',
      );
      await nextPoll(tester);

      await tester.tap(find.byKey(Key('station-resolve-reprint-${unclear['id']}')));
      await tester.pumpAndSettle();

      expect(app.backend.stationWrites.single.body, {'resolution': 'reprint'});
      expect(find.text('BAR 0141 · Table 12 · #${order['id']} · reprint'), findsOneWidget);
    });

    testWidgets('a printed ticket can be reprinted, a waiting one not yet', (tester) async {
      final (app, bar, order) = await start(tester);
      final waiting = app.backend.addStationTicket(bar, order, status: 'pending');
      final printed = app.backend.addStationTicket(bar, order);
      await nextPoll(tester);

      final waitingButton = tester.widget<TextButton>(
        find.byKey(Key('station-reprint-${waiting['id']}')),
      );
      expect(waitingButton.onPressed, isNull);
      await tester.tap(find.byKey(Key('station-reprint-${printed['id']}')));
      await tester.pumpAndSettle();

      expect(app.backend.stationWrites.single.path, '/station/tickets/${printed['id']}/reprint');
    });
  });

  group('alerts', () {
    testWidgets('paper end raises a red alert with sound once sound is armed', (tester) async {
      final (app, _, _) = await start(tester, status: 'paper end', waiting: 3);

      expect(find.text('Bar: paper end, 3 tickets waiting'), findsOneWidget);
      await tester.pump(StationScreen.beepEvery);
      expect(app.station.beeps, 0, reason: 'browsers need a tap before playing sound');

      await tester.tap(find.byKey(const Key('station-arm')));
      await tester.pump();
      expect(app.station.armed, isTrue);
      expect(find.byKey(const Key('station-arm')), findsNothing);
      await tester.pump(StationScreen.beepEvery);
      expect(app.station.beeps, 1);
      await tester.pump(StationScreen.beepEvery);
      expect(app.station.beeps, 2);
    });

    testWidgets('the alert ends by itself once the printer is ready again', (tester) async {
      final (app, bar, _) = await start(tester, status: 'paper end', waiting: 3);
      await tester.tap(find.byKey(const Key('station-arm')));
      await tester.pump();
      expect(alert(), findsOneWidget);

      bar
        ..['status'] = 'ready'
        ..['waiting'] = 0;
      await nextPoll(tester);
      final beeps = app.station.beeps;
      await tester.pump(StationScreen.beepEvery);

      expect(alert(), findsNothing);
      expect(app.station.beeps, beeps);
    });

    testWidgets('unclear tickets and long waits alert', (tester) async {
      await start(tester, waiting: 2, unclear: 1, oldestWaitingSeconds: 45);

      expect(find.text('Bar: 2 tickets waiting for 45 s'), findsOneWidget);
      expect(find.text('Bar: 1 unclear ticket, check the pile'), findsOneWidget);
    });

    testWidgets('a short wait or an unchecked printer does not alert', (tester) async {
      await start(tester, status: 'unknown', waiting: 1, oldestWaitingSeconds: 10);

      expect(alert(), findsNothing);
    });

    testWidgets('losing the connection for more than 10 seconds alerts', (tester) async {
      final (app, _, _) = await start(tester);
      app.backend.offline = true;

      for (var i = 0; i < 5; i++) {
        await nextPoll(tester);
      }
      expect(alert(), findsNothing);
      await nextPoll(tester);
      expect(find.text('No connection to the server'), findsOneWidget);

      app.backend.offline = false;
      await nextPoll(tester);
      expect(alert(), findsNothing);
    });
  });

  group('cancel at the station', () {
    Future<void> openOrder(WidgetTester tester, Map<String, dynamic> ticket) async {
      await tester.tap(find.byKey(Key('station-ticket-${ticket['id']}')));
      await tester.pumpAndSettle();
    }

    Future<void> confirm(
      WidgetTester tester, {
      required String pin,
      String reason = 'sold out',
    }) async {
      await tester.enterText(find.byKey(const Key('confirm-reason')), reason);
      await tester.enterText(find.byKey(const Key('confirm-username')), 'anna');
      await tester.enterText(find.byKey(const Key('confirm-pin')), pin);
      await tester.tap(find.byKey(const Key('confirm-submit')));
      await tester.pumpAndSettle();
    }

    testWidgets('a waiter cancels a drink with her PIN', (tester) async {
      final (app, bar, order) = await start(tester);
      final ticket = app.backend.addStationTicket(bar, order);
      await nextPoll(tester);
      await openOrder(tester, ticket);
      final line = (order['lines'] as List).first as Map<String, dynamic>;

      await tester.tap(find.byKey(Key('station-cancel-${line['id']}')));
      await tester.pumpAndSettle();
      final pinField = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const Key('confirm-pin')),
          matching: find.byType(EditableText),
        ),
      );
      expect(pinField.obscureText, isTrue);
      await confirm(tester, pin: '4711');

      expect(app.backend.stationWrites.single.body, {
        'username': 'anna',
        'pin': '4711',
        'reason': 'sold out',
      });
      expect(find.text('Cancelled by Anna at Station Bar: sold out'), findsOneWidget);
      expect(find.byKey(Key('station-cancel-${line['id']}')), findsNothing);
    });

    testWidgets('a wrong PIN is shown and the PIN field is emptied', (tester) async {
      final (app, bar, order) = await start(tester);
      final ticket = app.backend.addStationTicket(bar, order);
      await nextPoll(tester);
      await openOrder(tester, ticket);
      final line = (order['lines'] as List).first as Map<String, dynamic>;
      await tester.tap(find.byKey(Key('station-cancel-${line['id']}')));
      await tester.pumpAndSettle();

      await confirm(tester, pin: '0000');

      expect(find.text('Wrong username or PIN'), findsOneWidget);
      final pin = tester.widget<TextField>(find.byKey(const Key('confirm-pin')));
      expect(pin.controller!.text, isEmpty);
      expect(line['status'], 'sent');

      await tester.enterText(find.byKey(const Key('confirm-pin')), '4711');
      await tester.tap(find.byKey(const Key('confirm-submit')));
      await tester.pumpAndSettle();
      expect(line['status'], 'cancelled');
    });

    testWidgets('an order is voided at the station', (tester) async {
      final (app, bar, order) = await start(tester);
      final ticket = app.backend.addStationTicket(bar, order);
      await nextPoll(tester);
      await openOrder(tester, ticket);

      await tester.tap(find.byKey(const Key('station-void')));
      await tester.pumpAndSettle();
      await confirm(tester, pin: '4711', reason: 'double order');

      expect(order['status'], 'voided');
      expect(find.text('Voided by Anna at Station Bar: double order'), findsOneWidget);
      expect(find.byKey(const Key('station-void')), findsNothing);
    });
  });
}
