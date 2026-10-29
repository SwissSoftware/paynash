import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/export/file_saver.dart';

import 'support/app_harness.dart';

class _RecordingSaver implements FileSaver {
  final saved = <({String name, String mimeType, Uint8List bytes})>[];

  @override
  Future<void> save(String filename, String mimeType, Uint8List bytes) async =>
      saved.add((name: filename, mimeType: mimeType, bytes: bytes));
}

String _date(DateTime d) =>
    '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

Future<(AppHarness, _RecordingSaver)> start(WidgetTester tester, {bool admin = true}) async {
  final saver = _RecordingSaver();
  final previous = FileSaver.instance;
  FileSaver.instance = saver;
  addTearDown(() => FileSaver.instance = previous);
  final app = AppHarness(tester);
  app.backend.addStaff('anna', 'waiter', '4711');
  app.backend.addStaff('boss', 'admin', '1111');
  await app.start();
  await app.login(admin ? 'boss' : 'anna', admin ? '1111' : '4711');
  await tester.tap(find.text('History'));
  await tester.pumpAndSettle();
  return (app, saver);
}

void main() {
  testWidgets('an admin downloads the sent orders of the day shown', (tester) async {
    final (app, saver) = await start(tester);
    final today = _date(DateTime.now());

    await tester.tap(find.byKey(const Key('history-export')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('export-download')));
    await tester.pumpAndSettle();

    expect(app.backend.exportQueries.single, {'from': today, 'to': today, 'status': 'sent'});
    final file = saver.saved.single;
    expect(file.name, 'orders_${today}_$today.csv');
    expect(file.mimeType, 'text/csv');
    // Passed on byte for byte, so the byte-order mark Excel needs stays in.
    expect(file.bytes.sublist(0, 3), [0xEF, 0xBB, 0xBF]);
    expect(utf8.decode(file.bytes.sublist(3)), 'order_id,table\r\n');
    expect(find.text('Downloaded orders_${today}_$today.csv'), findsOneWidget);
  });

  testWidgets('the status filter is sent along', (tester) async {
    final (app, _) = await start(tester);
    await tester.tap(find.byKey(const Key('history-prev-day')));
    await tester.pumpAndSettle();
    final yesterday = _date(DateTime.now().subtract(const Duration(days: 1)));

    await tester.tap(find.byKey(const Key('history-export')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('All'));
    await tester.tap(find.byKey(const Key('export-download')));
    await tester.pumpAndSettle();

    expect(app.backend.exportQueries.single, {'from': yesterday, 'to': yesterday, 'status': 'all'});
  });

  testWidgets('a rejected export keeps the dialog open with the reason', (tester) async {
    final (app, saver) = await start(tester);
    // Simulate the server refusing, e.g. because the admin was demoted meanwhile.
    app.backend.staff.firstWhere((s) => s.username == 'boss').role = 'waiter';

    await tester.tap(find.byKey(const Key('history-export')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('export-download')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('export-error')), findsOneWidget);
    expect(saver.saved, isEmpty);
  });

  testWidgets('waiters have no export', (tester) async {
    await start(tester, admin: false);
    expect(find.byKey(const Key('history-export')), findsNothing);
  });
}
