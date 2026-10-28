import 'package:flutter/material.dart';

import 'api/api_client.dart';
import 'auth/login_screen.dart';
import 'auth/session.dart';
import 'auth/token_store.dart';
import 'config.dart';
import 'home/home_shell.dart';

void main() {
  final api = ApiClient(baseUrl: apiBaseUrl, tokenStore: SharedPreferencesTokenStore());
  runApp(PosApp(session: SessionController(api)..restore()));
}

class PosApp extends StatelessWidget {
  const PosApp({super.key, required this.session});

  final SessionController session;

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
          SessionStatus.loggedIn => HomeShell(session: session),
        },
      ),
    );
  }
}
