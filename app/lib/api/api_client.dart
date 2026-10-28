import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;

import '../auth/token_store.dart';

/// An error response from the backend (RFC 9457 problem detail).
class ApiError implements Exception {
  ApiError(this.status, this.message, [this.fieldErrors = const {}, this.details = const {}]);

  final int status;
  final String message;
  final Map<String, String> fieldErrors;

  /// Extra members of the problem response, e.g. `menuItemInactive`.
  final Map<String, dynamic> details;

  /// The server could not be reached at all.
  bool get isConnectionError => status == 0;

  @override
  String toString() => message;
}

/// JSON client for the POS backend. Adds the bearer token and reports 401
/// responses to [onUnauthorized] so the app can return to the login screen.
class ApiClient {
  ApiClient({required this.baseUrl, required this.tokenStore, http.Client? httpClient})
    : _http = httpClient ?? http.Client();

  final String baseUrl;
  final TokenStore tokenStore;
  final http.Client _http;

  /// Set by the session controller.
  void Function()? onUnauthorized;

  Future<dynamic> get(String path) => _send('GET', path);

  /// With [idempotencyKey] the request is retried once with the same key if
  /// the connection fails, so a lost response cannot cause a second effect
  /// (design D5). Create one key per user action with [newIdempotencyKey].
  Future<dynamic> post(String path, [Object? body, String? idempotencyKey]) =>
      _send('POST', path, body, idempotencyKey);

  Future<dynamic> put(String path, [Object? body, String? idempotencyKey]) =>
      _send('PUT', path, body, idempotencyKey);

  Future<dynamic> delete(String path, [String? idempotencyKey]) =>
      _send('DELETE', path, null, idempotencyKey);

  /// Uploads one file as multipart field "file".
  Future<dynamic> upload(String path, List<int> bytes, String filename) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'))
      ..files.add(http.MultipartFile.fromBytes('file', bytes, filename: filename));
    return _decode(await _execute(request));
  }

  /// GET with `If-None-Match`. Returns null when the server answers 304.
  Future<({dynamic body, String? etag})?> getIfChanged(String path, String? etag) async {
    final request = http.Request('GET', Uri.parse('$baseUrl$path'));
    if (etag != null) request.headers['If-None-Match'] = etag;
    final response = await _execute(request);
    if (response.statusCode == 304) return null;
    return (body: _decode(response), etag: response.headers['etag']);
  }

  /// Turns a server-relative URL such as "/images/x.webp" into an absolute one.
  String resolve(String url) => url.startsWith('http') ? url : '$baseUrl$url';

  Future<dynamic> _send(String method, String path, [Object? body, String? idempotencyKey]) async {
    http.Request build() {
      final request = http.Request(method, Uri.parse('$baseUrl$path'));
      if (body != null) {
        request.headers['Content-Type'] = 'application/json';
        request.body = jsonEncode(body);
      }
      if (idempotencyKey != null) {
        request.headers['Idempotency-Key'] = idempotencyKey;
      }
      return request;
    }

    try {
      return _decode(await _execute(build()));
    } on ApiError catch (e) {
      if (idempotencyKey == null || !e.isConnectionError) rethrow;
      return _decode(await _execute(build()));
    }
  }

  Future<http.Response> _execute(http.BaseRequest request) async {
    final token = await tokenStore.read();
    request.headers['Accept'] = 'application/json';
    if (token != null) {
      request.headers['Authorization'] = 'Bearer $token';
    }

    final http.Response response;
    try {
      response = await http.Response.fromStream(await _http.send(request));
    } catch (_) {
      throw ApiError(0, 'No connection to the server');
    }

    if (response.statusCode == 401 && token != null) {
      onUnauthorized?.call();
    }
    if (response.statusCode >= 400) {
      throw _toError(response);
    }
    return response;
  }

  static dynamic _decode(http.Response response) =>
      response.body.isEmpty ? null : jsonDecode(utf8.decode(response.bodyBytes));

  static ApiError _toError(http.Response response) {
    try {
      final json = jsonDecode(utf8.decode(response.bodyBytes));
      final errors = (json['errors'] as Map<String, dynamic>? ?? {}).map(
        (k, v) => MapEntry(k, v.toString()),
      );
      final message =
          json['detail'] as String? ?? json['title'] as String? ?? 'Error ${response.statusCode}';
      return ApiError(response.statusCode, message, errors, json as Map<String, dynamic>);
    } catch (_) {
      return ApiError(response.statusCode, 'Error ${response.statusCode}');
    }
  }
}

final _random = Random.secure();

/// A random (version 4) UUID for the `Idempotency-Key` header.
String newIdempotencyKey() {
  final b = List<int>.generate(16, (_) => _random.nextInt(256));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  final hex = b.map((x) => x.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-${hex.substring(20)}';
}
