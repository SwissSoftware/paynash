import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:paynash_pos/net/site_trust_io.dart';

/// Creates a CA and a localhost server certificate signed by it in [dir].
/// The server certificate is short-lived like Caddy's: Apple platforms reject
/// TLS server certificates valid for more than 825 days.
Future<void> createCertificates(String dir) async {
  Future<void> openssl(List<String> args) async {
    final result = await Process.run('openssl', args, workingDirectory: dir);
    if (result.exitCode != 0) {
      throw StateError('openssl ${args.first}: ${result.stderr}');
    }
  }

  File('$dir/ext.cnf').writeAsStringSync(
    'subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\nkeyUsage=critical,digitalSignature\n',
  );
  const ec = ['-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:P-256', '-nodes'];
  await openssl([
    'req',
    '-x509',
    ...ec,
    '-keyout',
    'ca.key',
    '-out',
    'ca.crt',
    '-days',
    '1',
    '-subj',
    '/CN=Paynash Test CA',
    '-addext',
    'basicConstraints=critical,CA:TRUE',
    '-addext',
    'keyUsage=critical,keyCertSign',
  ]);
  await openssl([
    'req',
    ...ec,
    '-keyout',
    'server.key',
    '-out',
    'server.csr',
    '-subj',
    '/CN=localhost',
  ]);
  await openssl([
    'x509',
    '-req',
    '-in',
    'server.csr',
    '-CA',
    'ca.crt',
    '-CAkey',
    'ca.key',
    '-CAcreateserial',
    '-out',
    'server.crt',
    '-days',
    '1',
    '-extfile',
    'ext.cnf',
  ]);
}

/// An HTTPS server whose certificate is signed by a CA that is not in the
/// system trust store, like Caddy's local CA with POS_TLS=internal.
void main() {
  late Directory certs;
  late HttpServer server;
  late Uri url;

  setUpAll(() async {
    certs = Directory.systemTemp.createTempSync('site_trust_test');
    await createCertificates(certs.path);
  });

  tearDownAll(() => certs.deleteSync(recursive: true));

  setUp(() async {
    final context = SecurityContext()
      ..useCertificateChain('${certs.path}/server.crt')
      ..usePrivateKey('${certs.path}/server.key');
    server = await HttpServer.bindSecure('localhost', 0, context);
    server.listen(
      (request) => request.response
        ..write('{"status":"UP"}')
        ..close(),
    );
    url = Uri.parse('https://localhost:${server.port}/api/health');
  });

  tearDown(() => server.close(force: true));

  Future<String> get() => http.read(url);

  test('without the site root certificate the handshake fails', () async {
    await expectLater(get(), throwsA(isA<HandshakeException>()));
  });

  test('with the site root certificate the app connects', () async {
    final overrides = SiteTrustOverrides(
      File('${certs.path}/ca.crt').readAsBytesSync(),
    );

    expect(
      await HttpOverrides.runWithHttpOverrides(get, overrides),
      '{"status":"UP"}',
    );
  });

  test('installSiteTrust without a certificate changes nothing', () {
    installSiteTrust('');

    expect(HttpOverrides.current, isNull);
  });
}
