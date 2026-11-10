import 'dart:convert';
import 'dart:io';

void installSiteTrust(String rootCaBase64) {
  if (rootCaBase64.isEmpty) return;
  HttpOverrides.global = SiteTrustOverrides(base64Decode(rootCaBase64));
}

/// Creates HTTP clients that trust the system roots plus [rootCaPem].
class SiteTrustOverrides extends HttpOverrides {
  SiteTrustOverrides(this.rootCaPem);

  final List<int> rootCaPem;

  @override
  HttpClient createHttpClient(SecurityContext? context) =>
      super.createHttpClient(
        SecurityContext(withTrustedRoots: true)
          ..setTrustedCertificatesBytes(rootCaPem),
      );
}
