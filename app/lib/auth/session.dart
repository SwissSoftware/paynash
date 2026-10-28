import 'package:flutter/foundation.dart';

import '../api/api_client.dart';

enum Role { admin, waiter }

class Me {
  Me({required this.id, required this.username, required this.displayName, required this.role});

  factory Me.fromJson(Map<String, dynamic> json) => Me(
        id: json['id'] as int,
        username: json['username'] as String,
        displayName: json['displayName'] as String,
        role: Role.values.byName(json['role'] as String),
      );

  final int id;
  final String username;
  final String displayName;
  final Role role;

  bool get isAdmin => role == Role.admin;
}

enum SessionStatus { restoring, loggedOut, loggedIn }

/// Holds who is logged in. Any 401 from the API logs the user out.
class SessionController extends ChangeNotifier {
  SessionController(this.api) {
    api.onUnauthorized = _expire;
  }

  final ApiClient api;

  SessionStatus status = SessionStatus.restoring;
  Me? me;

  /// Shown on the login screen after an automatic logout.
  String? notice;

  /// Restores a saved session on app start.
  Future<void> restore() async {
    if (await api.tokenStore.read() == null) {
      _set(SessionStatus.loggedOut, null);
      return;
    }
    try {
      _set(SessionStatus.loggedIn,
          Me.fromJson(await api.get('/auth/me') as Map<String, dynamic>));
    } on ApiError {
      await api.tokenStore.clear();
      _set(SessionStatus.loggedOut, null);
    }
  }

  /// Throws [ApiError] on failure.
  Future<void> login(String username, String pin) async {
    await api.tokenStore.clear();
    final json = await api.post('/auth/login', {'username': username, 'pin': pin})
        as Map<String, dynamic>;
    await api.tokenStore.write(json['token'] as String);
    notice = null;
    _set(SessionStatus.loggedIn, Me.fromJson(json['staff'] as Map<String, dynamic>));
  }

  Future<void> logout() async {
    try {
      await api.post('/auth/logout');
    } on ApiError {
      // The session is ended locally either way.
    }
    await api.tokenStore.clear();
    _set(SessionStatus.loggedOut, null);
  }

  void _expire() {
    if (status != SessionStatus.loggedIn) return;
    api.tokenStore.clear();
    notice = 'Your session has ended. Please log in again.';
    _set(SessionStatus.loggedOut, null);
  }

  void _set(SessionStatus s, Me? m) {
    status = s;
    me = m;
    notifyListeners();
  }
}
