import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'auth/login_screen.dart';
import 'auth/session.dart';
import 'auth/token_store.dart';
import 'config.dart';
import 'home/home_shell.dart';
import 'net/site_trust.dart';
import 'station/station_devices.dart';

void main() {
  // The session restore below reads the token over a platform channel (secure storage on mobile).
  WidgetsFlutterBinding.ensureInitialized();
  installSiteTrust();
  final api = ApiClient(baseUrl: apiBaseUrl, tokenStore: TokenStore.forPlatform());
  runApp(
    PosApp(session: SessionController(api)..restore(), stationDevices: StationDevices.platform()),
  );
}

class PosApp extends StatelessWidget {
  const PosApp({super.key, required this.session, required this.stationDevices});

  final SessionController session;

  /// Sound and keep-awake for the station screen; tests pass fakes.
  final StationDevices stationDevices;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Paynash POS',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: ListenableBuilder(
        listenable: session,
        builder: (context, _) => switch (session.status) {
          SessionStatus.restoring =>
            const Scaffold(body: Center(child: CircularProgressIndicator())),
          SessionStatus.loggedOut => LoginScreen(session: session),
          SessionStatus.loggedIn => HomeShell(session: session, stationDevices: stationDevices),
        },
      ),
    );
  }
}
