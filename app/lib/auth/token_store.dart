import 'package:shared_preferences/shared_preferences.dart';

/// Where the session token is kept between app starts. On web this is
/// browser storage; the mobile apps will use Keychain/Keystore (task 10.2).
abstract class TokenStore {
  Future<String?> read();
  Future<void> write(String token);
  Future<void> clear();
}

class SharedPreferencesTokenStore implements TokenStore {
  static const _key = 'session_token';

  @override
  Future<String?> read() async =>
      (await SharedPreferences.getInstance()).getString(_key);

  @override
  Future<void> write(String token) async =>
      (await SharedPreferences.getInstance()).setString(_key, token);

  @override
  Future<void> clear() async =>
      (await SharedPreferences.getInstance()).remove(_key);
}

class InMemoryTokenStore implements TokenStore {
  String? token;

  @override
  Future<String?> read() async => token;

  @override
  Future<void> write(String token) async => this.token = token;

  @override
  Future<void> clear() async => token = null;
}
