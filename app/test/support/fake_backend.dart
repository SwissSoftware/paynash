import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class FakeStaff {
  FakeStaff(this.id, this.username, this.displayName, this.role, this.pin);
  final int id;
  final String username;
  String displayName;
  String role;
  String pin;
  bool active = true;

  Map<String, dynamic> toJson() => {
    'id': id,
    'username': username,
    'displayName': displayName,
    'role': role,
    'active': active,
  };
}

/// In-memory stand-in for the backend's auth and staff endpoints.
class FakeBackend {
  final staff = <FakeStaff>[];
  final sessions = <String, FakeStaff>{};
  int _nextId = 1;

  FakeStaff addStaff(String username, String role, String pin, {String? displayName}) {
    final s = FakeStaff(_nextId++, username, displayName ?? username, role, pin);
    staff.add(s);
    return s;
  }

  void expireAllSessions() => sessions.clear();

  // --- menu

  final categories = <Map<String, dynamic>>[];
  final items = <Map<String, dynamic>>[];
  int _nextMenuId = 100;
  int menuRequests = 0;
  int uploads = 0;

  int addCategory(String name) {
    final id = _nextMenuId++;
    categories.add({'id': id, 'name': name, 'displayOrder': categories.length});
    return id;
  }

  int addItem(
    int categoryId,
    String name,
    String type,
    String price, {
    bool active = true,
    String? imageUrl,
  }) {
    final id = _nextMenuId++;
    items.add({
      'id': id,
      'categoryId': categoryId,
      'name': name,
      'type': type,
      'price': price,
      'description': null,
      'displayOrder': items.where((i) => i['categoryId'] == categoryId).length,
      'active': active,
      'imageUrl': imageUrl,
    });
    return id;
  }

  Map<String, dynamic> _menuJson() => {
    'categories': [
      for (final c in categories)
        if (items.any((i) => i['categoryId'] == c['id'] && i['active'] == true))
          {
            'id': c['id'],
            'name': c['name'],
            'items': [
              for (final i in items)
                if (i['categoryId'] == c['id'] && i['active'] == true)
                  {
                    'id': i['id'],
                    'name': i['name'],
                    'type': i['type'],
                    'price': i['price'],
                    'description': i['description'],
                    'imageUrl': i['imageUrl'],
                  },
            ],
          },
    ],
  };

  http.Response? _menuRoutes(http.Request r, Map<String, dynamic>? body) {
    final path = r.url.path;
    if (path == '/menu') {
      menuRequests++;
      final json = jsonEncode(_menuJson());
      final etag = '"${json.hashCode}"';
      if (r.headers['If-None-Match'] == etag) return http.Response('', 304);
      return http.Response(
        json,
        200,
        headers: {'content-type': 'application/json; charset=utf-8', 'etag': etag},
      );
    }
    if (path == '/admin/categories' && r.method == 'GET') return _json(200, categories);
    if (path == '/admin/categories' && r.method == 'POST') {
      final id = addCategory(body!['name'] as String);
      return _json(201, categories.firstWhere((c) => c['id'] == id));
    }
    final cat = RegExp(r'^/admin/categories/(\d+)$').firstMatch(path);
    if (cat != null && r.method == 'DELETE') {
      final id = int.parse(cat.group(1)!);
      final names = [
        for (final i in items)
          if (i['categoryId'] == id) i['name'],
      ];
      if (names.isNotEmpty) {
        return _json(409, {
          'status': 409,
          'detail': 'The category still contains items: ${names.join(', ')}',
          'items': names,
        });
      }
      categories.removeWhere((c) => c['id'] == id);
      return http.Response('', 204);
    }
    if (path == '/admin/items' && r.method == 'GET') return _json(200, items);
    if (path == '/admin/items' && r.method == 'POST') {
      final errors = _validateItem(body!);
      if (errors.isNotEmpty) return _problem(400, 'Validation failed', errors);
      final id = addItem(
        body['categoryId'] as int,
        body['name'] as String,
        body['type'] as String,
        body['price'] as String,
        active: body['active'] as bool? ?? true,
      );
      return _json(201, items.firstWhere((i) => i['id'] == id));
    }
    final item = RegExp(r'^/admin/items/(\d+)(/image)?$').firstMatch(path);
    if (item != null) {
      final i = items.firstWhere((i) => i['id'] == int.parse(item.group(1)!));
      if (item.group(2) != null) {
        if (r.method == 'DELETE') {
          i['imageUrl'] = null;
        } else {
          uploads++;
          i['imageUrl'] = '/images/upload-$uploads.webp';
        }
        return _json(200, i);
      }
      final errors = _validateItem(body!);
      if (errors.isNotEmpty) return _problem(400, 'Validation failed', errors);
      i
        ..['categoryId'] = body['categoryId']
        ..['name'] = body['name']
        ..['type'] = body['type']
        ..['price'] = body['price']
        ..['active'] = body['active'];
      return _json(200, i);
    }
    return null;
  }

  static Map<String, String> _validateItem(Map<String, dynamic> b) => {
    if ((b['name'] as String? ?? '').trim().isEmpty) 'name': 'must not be blank',
    if (b['type'] == null) 'type': 'Type is required (food or drink)',
    if (!RegExp(r'^\d{1,5}(\.\d{1,2})?$').hasMatch(b['price'] as String? ?? ''))
      'price': 'Price must be at least 0.00 with at most two decimals',
  };

  // --- orders

  final orders = <Map<String, dynamic>>[];
  int _nextOrderId = 1000;
  int _nextLineId = 5000;

  /// When true, every request fails as if the server were unreachable.
  bool offline = false;

  /// The next this many sends are handled but their answers are lost, as on a
  /// dropped WiFi connection.
  int loseSendAnswers = 0;

  /// Method, path, and Idempotency-Key of every changing order request.
  final orderWrites = <({String method, String path, String? key})>[];

  /// Answers already given per idempotency key, as the real server replays them.
  final _replies = <String, http.Response>{};

  /// Saves a sent order, as another device would. [lines] are (item id, qty).
  Map<String, dynamic> addSentOrder(
    String table,
    FakeStaff waiter,
    List<(int, int)> lines, {
    String sentAt = '2026-10-29T18:30:00Z',
  }) {
    final order = {
      'id': _nextOrderId++,
      'tableLabel': table,
      'status': 'sent',
      'waiter': waiter.displayName,
      'sentAt': sentAt,
      'version': 0,
      'voidReason': null,
      'lines': <Map<String, dynamic>>[for (final (itemId, qty) in lines) _line(itemId, qty, null)],
    };
    orders.add(order);
    return order;
  }

  Map<String, dynamic> _line(int itemId, int qty, String? note) {
    final item = items.firstWhere((i) => i['id'] == itemId);
    return {
      'id': _nextLineId++,
      'menuItemId': itemId,
      'name': item['name'],
      'type': item['type'],
      'unitPrice': item['price'],
      'qty': qty,
      'note': note,
      'status': 'sent',
      'cancelReason': null,
    };
  }

  Map<String, dynamic> order(int id) => orders.firstWhere((o) => o['id'] == id);

  static int _rappen(String price) => int.parse(price.replaceAll('.', ''));

  static String _chf(int rappen) => '${rappen ~/ 100}.${(rappen % 100).toString().padLeft(2, '0')}';

  static int _lineTotal(Map<String, dynamic> l) =>
      l['status'] == 'cancelled' ? 0 : (l['qty'] as int) * _rappen(l['unitPrice'] as String);

  Map<String, dynamic> _orderJson(Map<String, dynamic> o) {
    final lines = (o['lines'] as List).cast<Map<String, dynamic>>();
    return {
      'id': o['id'],
      'tableLabel': o['tableLabel'],
      'status': o['status'],
      'waiter': o['waiter'],
      'sentAt': o['sentAt'],
      'version': o['version'],
      'voidReason': o['voidReason'],
      'total': _chf(lines.fold(0, (sum, l) => sum + _lineTotal(l))),
      'lines': [
        for (final l in lines) {...l, 'lineTotal': _chf(_lineTotal(l))},
      ],
    };
  }

  http.Response? _orderRoutes(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    if (!path.startsWith('/orders') && !path.startsWith('/admin/orders')) return null;
    final key = r.headers['Idempotency-Key'];
    if (r.method == 'GET') return _orderRoute(r, body, me);
    orderWrites.add((method: r.method, path: path, key: key));
    final replyKey = '${me.id}:$key';
    final response = key != null && _replies.containsKey(replyKey)
        ? _replies[replyKey]
        : _orderRoute(r, body, me);
    if (key != null && response != null) _replies[replyKey] = response;
    if (path == '/orders' && loseSendAnswers > 0) {
      loseSendAnswers--;
      throw http.ClientException('Connection lost', r.url);
    }
    return response;
  }

  http.Response? _orderRoute(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    if (path == '/orders' && r.method == 'GET') {
      final date = r.url.queryParameters['date'];
      return _json(200, [
        for (final o in orders.reversed.where(
          (o) => date == null || (o['sentAt'] as String).startsWith(date),
        ))
          {
            'id': o['id'],
            'tableLabel': o['tableLabel'],
            'status': o['status'],
            'waiter': o['waiter'],
            'sentAt': o['sentAt'],
            'total': _orderJson(o)['total'],
          },
      ]);
    }
    if (path == '/orders' && r.method == 'POST') {
      final label = (body!['tableLabel'] as String).trim().toUpperCase();
      if (!RegExp(r'^[A-Z0-9]{1,10}$').hasMatch(label)) {
        return _json(400, {
          'status': 400,
          'detail': 'Validation failed',
          'errors': {'tableLabel': 'Table must be 1 to 10 letters or digits'},
        });
      }
      final lines = (body['lines'] as List).cast<Map<String, dynamic>>();
      final inactive = [for (final l in lines) items.firstWhere((i) => i['id'] == l['menuItemId'])]
          .where((i) => i['active'] != true)
          .toSet();
      if (inactive.isNotEmpty) {
        return _json(409, {
          'status': 409,
          'detail': '${inactive.map((i) => i['name']).join(', ')} no longer available',
          'inactiveMenuItemIds': [for (final i in inactive) i['id']],
        });
      }
      final order = addSentOrder(label, me, const []);
      for (final l in lines) {
        final note = (l['note'] as String?)?.trim();
        (order['lines'] as List).add(
          _line(
            l['menuItemId'] as int,
            l['qty'] as int,
            note == null || note.isEmpty ? null : note,
          ),
        );
      }
      return _json(201, _orderJson(order));
    }
    final detail = RegExp(r'^/orders/(\d+)$').firstMatch(path);
    if (detail != null) {
      final json = jsonEncode(_orderJson(order(int.parse(detail.group(1)!))));
      final etag = '"${json.hashCode}"';
      if (r.headers['If-None-Match'] == etag) return http.Response('', 304);
      return http.Response(
        json,
        200,
        headers: {'content-type': 'application/json; charset=utf-8', 'etag': etag},
      );
    }
    final admin = RegExp(r'^/admin/orders/(\d+)/(?:lines/(\d+)/cancel|void)$').firstMatch(path);
    if (admin == null) return null;
    final o = order(int.parse(admin.group(1)!));
    if (o['status'] != 'sent') {
      return _problem(409, 'The order is ${o['status']} and can no longer be changed');
    }
    final lines = (o['lines'] as List).cast<Map<String, dynamic>>();
    final reason = body!['reason'] as String;
    if (admin.group(2) != null) {
      final line = lines.firstWhere((l) => l['id'] == int.parse(admin.group(2)!));
      if (line['status'] == 'cancelled') return _problem(409, 'The line is already cancelled');
      line
        ..['status'] = 'cancelled'
        ..['cancelReason'] = reason;
    } else {
      if (body['version'] != null && body['version'] != o['version']) {
        return _problem(409, 'The order was changed meanwhile. Please check it and try again.');
      }
      for (final l in lines.where((l) => l['status'] == 'sent')) {
        l
          ..['status'] = 'cancelled'
          ..['cancelReason'] = reason;
      }
      o
        ..['status'] = 'voided'
        ..['voidReason'] = reason;
    }
    o['version'] = (o['version'] as int) + 1;
    return _json(200, _orderJson(o));
  }

  // --- tables

  final tables = <Map<String, dynamic>>[];
  int _nextTableId = 500;

  int addTable(String number, {String? name, bool active = true}) {
    final id = _nextTableId++;
    tables.add({'id': id, 'number': number, 'name': name, 'active': active});
    return id;
  }

  /// Numeric numbers first in numeric order, then the rest, like the backend.
  int _byNumber(Map<String, dynamic> a, Map<String, dynamic> b) {
    final x = a['number'] as String, y = b['number'] as String;
    final nx = int.tryParse(x), ny = int.tryParse(y);
    if (nx != null && ny != null) return nx.compareTo(ny);
    if (nx != null) return -1;
    if (ny != null) return 1;
    return x.compareTo(y);
  }

  http.Response? _tableRoutes(http.Request r, Map<String, dynamic>? body) {
    final path = r.url.path;
    if (path == '/tables') {
      return _json(200, [
        for (final t in tables.where((t) => t['active'] == true).toList()..sort(_byNumber))
          {'id': t['id'], 'number': t['number'], 'name': t['name']},
      ]);
    }
    if (path == '/admin/tables' && r.method == 'GET') {
      return _json(200, tables.toList()..sort(_byNumber));
    }
    final match = RegExp(r'^/admin/tables(?:/(\d+))?$').firstMatch(path);
    if (match == null) return null;
    final id = match.group(1) == null ? null : int.parse(match.group(1)!);
    if (r.method == 'DELETE') {
      tables.removeWhere((t) => t['id'] == id);
      return http.Response('', 204);
    }
    final number = (body!['number'] as String? ?? '').trim().toUpperCase();
    if (!RegExp(r'^[A-Z0-9]{1,10}$').hasMatch(number)) {
      return _problem(400, 'Validation failed', {
        'number': 'Number must be 1 to 10 letters or digits',
      });
    }
    if (tables.any((t) => t['number'] == number && t['id'] != id)) {
      return _problem(400, 'Validation failed', {'number': 'This number is already in use'});
    }
    if (id == null) {
      final newId = addTable(number, name: body['name'] as String?);
      return _json(201, tables.firstWhere((t) => t['id'] == newId));
    }
    final t = tables.firstWhere((t) => t['id'] == id)
      ..['number'] = number
      ..['name'] = body['name']
      ..['active'] = body['active'];
    return _json(200, t);
  }

  late final http.Client client = MockClient(_handle);

  Future<http.Response> _handle(http.Request r) async {
    if (offline) throw http.ClientException('Server unreachable', r.url);
    final path = r.url.path;
    final isJson = r.headers['Content-Type']?.startsWith('application/json') ?? false;
    final body = isJson && r.body.isNotEmpty ? jsonDecode(r.body) as Map<String, dynamic> : null;

    if (r.method == 'POST' && path == '/auth/login') {
      final match = staff.where(
        (s) =>
            s.username.toLowerCase() == (body!['username'] as String).toLowerCase() &&
            s.pin == body['pin'] &&
            s.active,
      );
      if (match.isEmpty) return _problem(401, 'Invalid credentials');
      final token = 'token-${sessions.length + 1}-${match.first.username}';
      sessions[token] = match.first;
      return _json(200, {
        'token': token,
        'expiresAt': '2030-01-01T00:00:00Z',
        'staff': match.first.toJson(),
      });
    }

    final auth = r.headers['Authorization'];
    final me = auth == null ? null : sessions[auth.substring('Bearer '.length)];
    if (me == null) return _problem(401, 'Authentication required');

    if (path == '/auth/me') return _json(200, me.toJson());
    if (path == '/auth/logout') {
      sessions.remove(auth!.substring('Bearer '.length));
      return http.Response('', 204);
    }

    if (path.startsWith('/admin/') && me.role != 'admin') {
      return _problem(403, 'Forbidden');
    }
    final tableResponse = _tableRoutes(r, body);
    if (tableResponse != null) return tableResponse;
    final menuResponse = _menuRoutes(r, body);
    if (menuResponse != null) return menuResponse;
    final orderResponse = _orderRoutes(r, body, me);
    if (orderResponse != null) return orderResponse;
    if (path == '/admin/staff' && r.method == 'GET') {
      return _json(200, [for (final s in staff) s.toJson()]);
    }
    if (path == '/admin/staff' && r.method == 'POST') {
      final username = body!['username'] as String;
      if (staff.any((s) => s.username.toLowerCase() == username.toLowerCase())) {
        return _problem(400, 'Validation failed', {'username': 'Username already exists'});
      }
      final s = addStaff(
        username,
        body['role'] as String,
        body['pin'] as String,
        displayName: body['displayName'] as String,
      );
      return _json(201, s.toJson());
    }
    final idMatch = RegExp(r'^/admin/staff/(\d+)(/pin)?$').firstMatch(path);
    if (idMatch != null) {
      final s = staff.firstWhere((s) => s.id == int.parse(idMatch.group(1)!));
      if (idMatch.group(2) != null) {
        s.pin = body!['pin'] as String;
        return http.Response('', 204);
      }
      s
        ..displayName = body!['displayName'] as String
        ..role = body['role'] as String
        ..active = body['active'] as bool;
      if (!s.active) sessions.removeWhere((_, v) => v == s);
      return _json(200, s.toJson());
    }
    return _problem(404, 'Not found');
  }

  static http.Response _json(int status, Object body) => http.Response(
    jsonEncode(body),
    status,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );

  static http.Response _problem(int status, String detail, [Map<String, String>? errors]) =>
      _json(status, {'status': status, 'detail': detail, 'errors': ?errors});
}
