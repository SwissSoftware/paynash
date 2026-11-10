import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/auth/token_store.dart';

void main() {
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  test('secure store writes, reads, and clears the session token', () async {
    final store = SecureTokenStore();
    expect(await store.read(), isNull);

    await store.write('abc123');
    expect(await store.read(), 'abc123');

    await store.clear();
    expect(await store.read(), isNull);
  });

  test('token survives a new store instance (app restart)', () async {
    await SecureTokenStore().write('abc123');

    expect(await SecureTokenStore().read(), 'abc123');
  });

  test('outside the web the platform store is the secure store', () {
    expect(TokenStore.forPlatform(), isA<SecureTokenStore>());
  });
}
