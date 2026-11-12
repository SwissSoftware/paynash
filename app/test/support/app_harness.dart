import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/api/api_client.dart';
import 'package:paynash_pos/auth/session.dart';
import 'package:paynash_pos/auth/token_store.dart';
import 'package:paynash_pos/main.dart';
import 'package:paynash_pos/station/station_devices.dart';

import 'fake_backend.dart';

/// Counts alert sounds and keep-awake calls instead of using the device.
class FakeStationDevices implements AlertSound, KeepAwake {
  int beeps = 0;
  bool armed = false;
  bool awake = false;

  @override
  Future<void> arm() async => armed = true;

  @override
  Future<void> beep() async => beeps++;

  @override
  Future<void> enable() async => awake = true;

  @override
  Future<void> disable() async => awake = false;

  StationDevices get devices => StationDevices(sound: this, keepAwake: this);
}

class AppHarness {
  AppHarness(this.tester);

  final WidgetTester tester;
  final backend = FakeBackend();
  final tokens = InMemoryTokenStore();
  final station = FakeStationDevices();

  Future<void> start() async {
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = ApiClient(
        baseUrl: 'http://backend', tokenStore: tokens, httpClient: backend.client);
    final session = SessionController(api);
    await tester.pumpWidget(PosApp(session: session, stationDevices: station.devices));
    await session.restore();
    await tester.pumpAndSettle();
  }

  Future<void> login(String username, String pin) async {
    await tester.enterText(find.byKey(const Key('login-username')), username);
    await tester.enterText(find.byKey(const Key('login-pin')), pin);
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
  }

  Future<void> logout() async {
    await tester.tap(find.byKey(const Key('logout')));
    await tester.pumpAndSettle();
  }

  Finder get loginScreen => find.byKey(const Key('login-submit'));

  Finder currentUser(String name) => find.byWidgetPredicate((w) =>
      w is Text && w.key == const Key('current-user') && w.data == name);
}
