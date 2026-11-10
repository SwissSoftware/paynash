import 'site_trust_stub.dart'
    if (dart.library.io) 'site_trust_io.dart'
    as platform;

/// Root certificate of the on-site server, base64-encoded PEM, set at build time:
/// `--dart-define=POS_ROOT_CA=$(base64 < paynash-root.crt | tr -d '\n')` (design D11).
///
/// Needed in the mobile apps when the server uses Caddy's own certificate
/// authority (`POS_TLS=internal`): Flutter's HTTP stack ignores root
/// certificates installed by the user on Android. Empty when the server has a
/// publicly trusted certificate.
const String siteRootCa = String.fromEnvironment('POS_ROOT_CA');

/// Makes every HTTPS connection of the app (API calls and images) trust
/// [siteRootCa] in addition to the system roots. Does nothing on web, where the
/// browser decides, or when no certificate was given.
void installSiteTrust() => platform.installSiteTrust(siteRootCa);
