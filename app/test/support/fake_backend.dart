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

  /// Method, path, and Idempotency-Key of every changing order request.
  final orderWrites = <({String method, String path, String? key})>[];

  Map<String, dynamic> openOrder(String table, FakeStaff waiter, {int ageMinutes = 0}) {
    final order = {
      'id': _nextOrderId++,
      'tableLabel': table,
      'status': 'open',
      'waiter': waiter.displayName,
      'ageMinutes': ageMinutes,
      'version': 0,
      'lines': <Map<String, dynamic>>[],
      'rounds': <int>[],
    };
    orders.add(order);
    return order;
  }

  Map<String, dynamic> order(int id) => orders.firstWhere((o) => o['id'] == id);

  /// Adds a line as another device would.
  Map<String, dynamic> addOrderLine(int orderId, int itemId, {int qty = 1, String? note}) {
    final item = items.firstWhere((i) => i['id'] == itemId);
    final line = {
      'id': _nextLineId++,
      'menuItemId': itemId,
      'name': item['name'],
      'type': item['type'],
      'unitPrice': item['price'],
      'qty': qty,
      'note': note,
      'status': 'unsent',
      'roundNumber': null,
      'version': 0,
      'cancelReason': null,
    };
    final order = this.order(orderId);
    (order['lines'] as List).add(line);
    order['version'] = (order['version'] as int) + 1;
    return line;
  }

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
      'version': o['version'],
      'total': _chf(lines.fold(0, (sum, l) => sum + _lineTotal(l))),
      'hasUnsentLines': lines.any((l) => l['status'] == 'unsent'),
      'lines': [
        for (final l in lines) {...l, 'lineTotal': _chf(_lineTotal(l))},
      ],
    };
  }

  http.Response? _orderRoutes(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    if (!path.startsWith('/orders')) return null;
    if (r.method != 'GET') {
      orderWrites.add((method: r.method, path: path, key: r.headers['Idempotency-Key']));
    }
    if (path == '/orders' && r.method == 'GET') {
      return _json(200, [
        for (final o in orders.where((o) => o['status'] == 'open'))
          {
            'id': o['id'],
            'tableLabel': o['tableLabel'],
            'waiter': o['waiter'],
            'total': _orderJson(o)['total'],
            'ageMinutes': o['ageMinutes'],
            'unsentLines': (o['lines'] as List).where((l) => l['status'] == 'unsent').length,
          },
      ]);
    }
    if (path == '/orders' && r.method == 'POST') {
      final label = (body!['tableLabel'] as String).trim().toUpperCase();
      final existing = orders.where((o) => o['tableLabel'] == label && o['status'] == 'open');
      if (existing.isNotEmpty) return _json(200, _orderJson(existing.first));
      return _json(201, _orderJson(openOrder(label, me)));
    }
    final m = RegExp(r'^/orders/(\d+)(?:/(lines|send)(?:/(\d+)(/cancel)?)?)?$').firstMatch(path);
    if (m == null) return null;
    final o = order(int.parse(m.group(1)!));
    final lines = (o['lines'] as List).cast<Map<String, dynamic>>();
    if (m.group(2) == null) {
      final json = jsonEncode(_orderJson(o));
      final etag = '"${json.hashCode}"';
      if (r.headers['If-None-Match'] == etag) return http.Response('', 304);
      return http.Response(
        json,
        200,
        headers: {'content-type': 'application/json; charset=utf-8', 'etag': etag},
      );
    }
    if (o['status'] != 'open') {
      return _problem(409, 'The order is ${o['status']} and can no longer be changed');
    }
    void changed() => o['version'] = (o['version'] as int) + 1;

    if (m.group(2) == 'send') {
      final unsent = lines.where((l) => l['status'] == 'unsent').toList();
      if (unsent.isEmpty) return _json(200, {'round': null, 'order': _orderJson(o)});
      final rounds = o['rounds'] as List<int>;
      final number = rounds.length + 1;
      rounds.add(number);
      for (final l in unsent) {
        l
          ..['status'] = 'sent'
          ..['roundNumber'] = number
          ..['version'] = (l['version'] as int) + 1;
      }
      changed();
      return _json(200, {
        'round': {
          'id': number,
          'number': number,
          'sentBy': me.displayName,
          'sentAt': '2026-10-28T12:00:00Z',
        },
        'order': _orderJson(o),
      });
    }
    if (m.group(3) == null && r.method == 'POST') {
      final item = items.firstWhere((i) => i['id'] == body!['menuItemId']);
      if (item['active'] != true) {
        return _json(409, {
          'status': 409,
          'detail': '${item['name']} is no longer available',
          'menuItemInactive': true,
        });
      }
      addOrderLine(
        o['id'] as int,
        item['id'] as int,
        qty: body!['qty'] as int,
        note: body['note'] as String?,
      );
      return _json(201, _orderJson(o));
    }
    final line = lines.firstWhere((l) => l['id'] == int.parse(m.group(3)!));
    if (m.group(4) != null) {
      line
        ..['status'] = 'cancelled'
        ..['cancelReason'] = body!['reason']
        ..['version'] = (line['version'] as int) + 1;
    } else if (r.method == 'DELETE') {
      lines.remove(line);
    } else {
      if (body!['version'] != line['version']) {
        return _problem(
          409,
          'The line was changed on another device. Please check it and try again.',
        );
      }
      final note = (body['note'] as String?)?.trim();
      line
        ..['qty'] = body['qty']
        ..['note'] = note == null || note.isEmpty ? null : note
        ..['version'] = (line['version'] as int) + 1;
    }
    changed();
    return _json(200, _orderJson(o));
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
