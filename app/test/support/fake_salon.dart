part of 'fake_backend.dart';

/// In-memory stand-in for the salon endpoints: settings, customers, invoices,
/// payments, receipts, and the sales report.
class FakeSalon {
  FakeSalon(this.backend);

  final FakeBackend backend;

  // --- settings

  Map<String, dynamic> settings = {
    'mode': 'event',
    'businessName': null,
    'street': null,
    'postalCode': null,
    'city': null,
    'phone': null,
    'email': null,
    'website': null,
    'iban': null,
    'accountHolder': null,
    'vatEnabled': false,
    'vatUid': null,
  };

  int settingsRequests = 0;

  bool get isSalon => settings['mode'] == 'salon';

  /// Switches to salon mode with complete business details.
  void useSalon({bool vat = false}) {
    settings = {
      ...settings,
      'mode': 'salon',
      'businessName': 'Kosmetik Aurora',
      'street': 'Bahnhofstrasse 1',
      'postalCode': '3000',
      'city': 'Bern',
      'vatEnabled': vat,
      'vatUid': vat ? 'CHE-116.281.710 MWST' : null,
    };
  }

  http.Response? _routes(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    if (path == '/settings' && r.method == 'GET') {
      settingsRequests++;
      return FakeBackend._json(200, settings);
    }
    if (path == '/admin/settings' && r.method == 'PUT') return _saveSettings(body!);
    if (path == '/admin/settings/mode' && r.method == 'PUT') {
      if (body!['mode'] != settings['mode'] && backend.orders.isNotEmpty) {
        return FakeBackend._problem(
          409,
          'The installation already has orders, so its mode can no longer change',
        );
      }
      settings = {...settings, 'mode': body['mode']};
      return FakeBackend._json(200, settings);
    }
    return _customerRoutes(r, body, me) ?? _invoiceRoutes(r, body, me) ?? _reportRoutes(r);
  }

  // --- customers

  final customers = <Map<String, dynamic>>[];
  int _nextCustomerId = 300;
  int _nextCustomerNumber = 1;

  /// Exported customer data files, by file name.
  final exports = <String>[];

  int addCustomer(String first, String last, {String? phone, String? email, bool active = true}) {
    final id = _nextCustomerId++;
    customers.add({
      'id': id,
      'number': _nextCustomerNumber++,
      'firstName': first,
      'lastName': last,
      'phone': phone,
      'email': email,
      'street': null,
      'postalCode': null,
      'city': null,
      'note': null,
      'active': active,
      'anonymized': false,
    });
    return id;
  }

  Map<String, dynamic> customer(int id) => customers.firstWhere((c) => c['id'] == id);

  static String customerName(Map<String, dynamic> c) => c['anonymized'] == true
      ? 'Deleted customer'
      : [c['firstName'], c['lastName']].whereType<String>().join(' ');

  Map<String, dynamic> _customerJson(Map<String, dynamic> c) => {...c, 'name': customerName(c)};

  http.Response? _customerRoutes(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    final q = r.url.queryParameters;
    if (path == '/customers' && r.method == 'GET') {
      final inactive = q['includeInactive'] == 'true';
      if (inactive && me.role != 'admin') {
        return FakeBackend._problem(403, 'Only admins can list inactive customers');
      }
      final query = (q['q'] ?? '').toLowerCase();
      final digits = query.replaceAll(RegExp(r'\D'), '');
      final list = customers.where((c) {
        if (c['active'] != true && !inactive) return false;
        if (query.isEmpty) return true;
        return customerName(c).toLowerCase().contains(query) ||
            (c['email'] as String? ?? '').toLowerCase().contains(query) ||
            '${c['number']}' == query ||
            (digits.length >= 3 &&
                (c['phone'] as String? ?? '').replaceAll(RegExp(r'\D'), '').contains(digits));
      }).toList()..sort((a, b) => customerName(a).compareTo(customerName(b)));
      return FakeBackend._json(200, [for (final c in list) _customerJson(c)]);
    }
    if (path == '/customers/duplicates' && r.method == 'GET') {
      String? norm(String? v) => v == null || v.trim().isEmpty ? null : v.trim().toLowerCase();
      final first = norm(q['firstName']);
      final last = norm(q['lastName']);
      return FakeBackend._json(200, [
        for (final c in customers)
          if (c['anonymized'] != true &&
              c['id'].toString() != q['exceptId'] &&
              (((first != null || last != null) &&
                      norm(c['firstName'] as String?) == first &&
                      norm(c['lastName'] as String?) == last) ||
                  (norm(q['email']) != null && norm(c['email'] as String?) == norm(q['email']))))
            _customerJson(c),
      ]);
    }
    if (path == '/customers' && r.method == 'POST') {
      final errors = _validateCustomer(body!);
      if (errors.isNotEmpty) return FakeBackend._problem(400, 'Validation failed', errors);
      final id = addCustomer(body['firstName'] as String, body['lastName'] as String);
      _applyCustomer(customer(id), body);
      return FakeBackend._json(201, _customerJson(customer(id)));
    }
    final one = RegExp(r'^/customers/(\d+)$').firstMatch(path);
    if (one != null) {
      final c = customer(int.parse(one.group(1)!));
      if (r.method == 'PUT') {
        final errors = _validateCustomer(body!);
        if (errors.isNotEmpty) return FakeBackend._problem(400, 'Validation failed', errors);
        _applyCustomer(c, body);
        return FakeBackend._json(200, _customerJson(c));
      }
      final mine = invoices.where((i) => i['customerId'] == c['id']).toList().reversed;
      final revenue = mine
          .where((i) => i['status'] != 'cancelled')
          .fold(0, (sum, i) => sum + FakeBackend._rappen(i['total'] as String));
      return FakeBackend._json(200, {
        'customer': _customerJson(c),
        'invoices': [
          for (final i in mine)
            {'id': i['id'], 'number': i['number'], 'date': i['date'], 'total': i['total'],
             'status': i['status']},
        ],
        'revenue': FakeBackend._chf(revenue),
      });
    }
    final admin = RegExp(r'^/admin/customers/(\d+)(?:/(deactivate|reactivate|export))?$')
        .firstMatch(path);
    if (admin != null) {
      final c = customer(int.parse(admin.group(1)!));
      switch (admin.group(2)) {
        case 'deactivate':
          c['active'] = false;
          return FakeBackend._json(200, _customerJson(c));
        case 'reactivate':
          c['active'] = true;
          return FakeBackend._json(200, _customerJson(c));
        case 'export':
          exports.add('Kunde-${c['number']}.json');
          return http.Response(
            jsonEncode({'customer': _customerJson(c)}),
            200,
            headers: {
              'content-type': 'application/json',
              'content-disposition': 'attachment; filename="Kunde-${c['number']}.json"',
            },
          );
      }
      if (r.method == 'DELETE') {
        final hasOrders = backend.orders.any((o) => o['customerId'] == c['id']);
        if (!hasOrders) {
          customers.remove(c);
          return FakeBackend._json(200, {'result': 'deleted'});
        }
        for (final f in ['firstName', 'lastName', 'phone', 'email', 'street', 'postalCode', 'city',
            'note']) {
          c[f] = null;
        }
        c
          ..['active'] = false
          ..['anonymized'] = true;
        return FakeBackend._json(200, {'result': 'anonymized'});
      }
    }
    return null;
  }

  static Map<String, String> _validateCustomer(Map<String, dynamic> b) {
    String text(String k) => (b[k] as String? ?? '').trim();
    return {
      if (text('firstName').isEmpty && text('lastName').isEmpty)
        'lastName': 'First name or last name is required',
      if (text('email').isNotEmpty && !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(text('email')))
        'email': 'Not a valid email address',
    };
  }

  static void _applyCustomer(Map<String, dynamic> c, Map<String, dynamic> b) {
    for (final f in ['firstName', 'lastName', 'phone', 'email', 'street', 'postalCode', 'city',
        'note']) {
      final v = (b[f] as String? ?? '').trim();
      c[f] = v.isEmpty ? null : v;
    }
  }

  // --- invoices

  final invoices = <Map<String, dynamic>>[];
  int _nextInvoiceId = 900;

  /// The invoice date of new invoices.
  String today = '2026-11-13';

  /// Status of new receipts while a receipt printer is active.
  String receiptStatus = 'printed';

  /// Answers to invoice requests by idempotency key, like the backend.
  final _issued = <String, http.Response>{};

  /// PDFs downloaded, by invoice number.
  final pdfs = <String>[];

  /// Copies printed, by invoice number.
  final copies = <String>[];

  bool get _hasReceiptPrinter =>
      backend.printers.any((p) => p['type'] == 'receipt' && p['active'] == true);

  Map<String, dynamic> _receipt(String kind) => _hasReceiptPrinter
      ? {'kind': kind, 'type': 'receipt', 'status': receiptStatus, 'label': 'COUNTER 0001'}
      : {'kind': kind, 'type': 'receipt', 'status': 'failed', 'error': 'no printer configured'};

  Map<String, dynamic> invoice(int id) => invoices.firstWhere((i) => i['id'] == id);

  /// The invoice an order shows: the active one, else the latest cancelled one.
  Map<String, dynamic>? invoiceOf(int orderId) {
    final mine = invoices.where((i) => i['orderId'] == orderId).toList();
    if (mine.isEmpty) return null;
    return mine.where((i) => i['status'] != 'cancelled').firstOrNull ?? mine.last;
  }

  Map<String, dynamic> _invoiceJson(Map<String, dynamic> i) {
    final c = customers.where((c) => c['id'] == i['customerId']).firstOrNull;
    return {
      ...i,
      'business': {'name': settings['businessName']},
      'customer': {
        'number': c?['number'] ?? 0,
        'name': i['customerName'],
        'street': c?['street'],
        'postalCode': c?['postalCode'],
        'city': c?['city'],
      },
      'payment': (i['paymentHistory'] as List).cast<Map<String, dynamic>>().where(
        (p) => p['removedBy'] == null,
      ).firstOrNull,
    };
  }

  http.Response? _invoiceRoutes(http.Request r, Map<String, dynamic>? body, FakeStaff me) {
    final path = r.url.path;
    final issue = RegExp(r'^/orders/(\d+)/invoice$').firstMatch(path);
    if (issue != null && r.method == 'POST') {
      final key = '${me.id}:${r.headers['Idempotency-Key']}';
      if (r.headers['Idempotency-Key'] != null && _issued.containsKey(key)) return _issued[key];
      final response = _issue(int.parse(issue.group(1)!), me);
      if (r.headers['Idempotency-Key'] != null) _issued[key] = response;
      return response;
    }
    if (path == '/invoices' && r.method == 'GET') {
      final q = r.url.queryParameters;
      final query = (q['q'] ?? '').toLowerCase();
      return FakeBackend._json(200, [
        for (final i in invoices.reversed)
          if ((q['from'] == null || (i['date'] as String).compareTo(q['from']!) >= 0) &&
              (q['to'] == null || (i['date'] as String).compareTo(q['to']!) <= 0) &&
              (q['status'] == null || i['status'] == q['status']) &&
              (query.isEmpty ||
                  (i['number'] as String).toLowerCase().contains(query) ||
                  (i['customerName'] as String).toLowerCase().contains(query)))
            {
              'id': i['id'],
              'number': i['number'],
              'date': i['date'],
              'customerNumber': _invoiceJson(i)['customer']['number'],
              'customerName': i['customerName'],
              'total': i['total'],
              'status': i['status'],
            },
      ]);
    }
    final one = RegExp(r'^/(admin/)?invoices/(\d+)(?:/(payment|receipt|pdf|cancel|payment/undo))?$')
        .firstMatch(path);
    if (one == null) return null;
    final i = invoice(int.parse(one.group(2)!));
    switch ((r.method, one.group(3))) {
      case ('GET', null):
        return FakeBackend._json(200, _invoiceJson(i));
      case ('GET', 'pdf'):
        pdfs.add(i['number'] as String);
        return http.Response.bytes(
          [37, 80, 68, 70],
          200,
          headers: {'content-disposition': 'attachment; filename="Rechnung-${i['number']}.pdf"'},
        );
      case ('POST', 'receipt'):
        final receipt = i['receipt'] as Map<String, dynamic>?;
        if (receipt != null && (receipt['status'] == 'pending' || receipt['status'] == 'printing')) {
          return FakeBackend._problem(409, 'The receipt is still being printed');
        }
        copies.add(i['number'] as String);
        i['receipt'] = _receipt('copy');
        return FakeBackend._json(201, i['receipt'] as Object);
      case ('POST', 'payment'):
        if (i['status'] == 'cancelled') {
          return FakeBackend._problem(409, 'A cancelled invoice cannot be paid');
        }
        if (i['status'] == 'paid') return FakeBackend._problem(409, 'The invoice is already paid');
        (i['paymentHistory'] as List).add({
          'method': body!['method'],
          'paidOn': body['paidOn'],
          'recordedBy': me.displayName,
          'removedBy': null,
          'removeReason': null,
        });
        i['status'] = 'paid';
        return FakeBackend._json(200, _invoiceJson(i));
      case ('POST', 'cancel'):
        if (i['status'] == 'paid') {
          return FakeBackend._problem(409, 'The invoice is paid, set the invoice back to open first');
        }
        i
          ..['status'] = 'cancelled'
          ..['cancelReason'] = body!['reason']
          ..['cancelledBy'] = me.displayName;
        return FakeBackend._json(200, _invoiceJson(i));
      case ('POST', 'payment/undo'):
        for (final p in (i['paymentHistory'] as List).cast<Map<String, dynamic>>()) {
          if (p['removedBy'] == null) {
            p
              ..['removedBy'] = me.displayName
              ..['removeReason'] = body!['reason'];
          }
        }
        i['status'] = 'open';
        return FakeBackend._json(200, _invoiceJson(i));
    }
    return null;
  }

  http.Response _issue(int orderId, FakeStaff me) {
    if (settings['street'] == null) {
      return FakeBackend._problem(
        409,
        'business address missing, ask an admin to complete the settings',
      );
    }
    final o = backend.order(orderId);
    if (o['status'] != 'sent') {
      return FakeBackend._problem(409, 'The order is ${o['status']} and cannot be invoiced');
    }
    final active = invoices.where((i) => i['orderId'] == orderId && i['status'] != 'cancelled');
    if (active.isNotEmpty) {
      return FakeBackend._problem(409, 'The order already has invoice ${active.first['number']}');
    }
    final lines = (o['lines'] as List).cast<Map<String, dynamic>>().where(
      (l) => l['status'] != 'cancelled',
    );
    final total = lines.fold(0, (sum, l) => sum + FakeBackend._lineTotal(l));
    final number = '2026-${(invoices.length + 1).toString().padLeft(4, '0')}';
    final i = {
      'id': _nextInvoiceId++,
      'number': number,
      'orderId': orderId,
      'customerId': o['customerId'],
      'customerName': o['customerName'],
      'date': today,
      'status': 'open',
      'lines': [
        for (final l in lines)
          {
            'qty': l['qty'],
            'name': l['name'],
            'unitPrice': l['unitPrice'],
            'vatRate': '8.1',
            'amount': FakeBackend._chf(FakeBackend._lineTotal(l)),
            'note': l['note'],
          },
      ],
      'total': FakeBackend._chf(total),
      'vatEnabled': settings['vatEnabled'],
      'vatUid': settings['vatUid'],
      'vat': <Object>[],
      'cancelReason': null,
      'cancelledBy': null,
      'paymentHistory': <Map<String, dynamic>>[],
      'receipt': _receipt('normal'),
    };
    invoices.add(i);
    return FakeBackend._json(201, _invoiceJson(i));
  }

  // --- sales report

  /// The report answered for any period; [reportRequests] records the periods.
  Map<String, dynamic> report = {
    'summary': {
      'revenue': '0.00',
      'invoiceCount': 0,
      'paid': '0.00',
      'open': '0.00',
      'byMethod': <Object>[],
      'vat': <Object>[],
    },
    'days': <Object>[],
    'services': <Object>[],
    'customers': <Object>[],
  };

  final reportRequests = <String>[];

  http.Response? _reportRoutes(http.Request r) {
    final q = r.url.queryParameters;
    if (r.url.path == '/admin/reports/sales') {
      reportRequests.add('${q['from']}..${q['to']}');
      return FakeBackend._json(200, {...report, 'from': q['from'], 'to': q['to']});
    }
    if (r.url.path == '/admin/reports/sales.xlsx') {
      return http.Response.bytes(
        [80, 75, 3, 4],
        200,
        headers: {
          'content-disposition': 'attachment; filename="Rapport_${q['from']}_${q['to']}.xlsx"',
        },
      );
    }
    return null;
  }

  http.Response _saveSettings(Map<String, dynamic> body) {
    String? text(String key) {
      final v = (body[key] as String?)?.trim();
      return v == null || v.isEmpty ? null : v;
    }

    final errors = <String, String>{};
    if (text('businessName') == null) errors['businessName'] = 'Business name is required';
    if (isSalon) {
      for (final f in ['street', 'postalCode', 'city']) {
        if (text(f) == null) errors[f] = 'Required in salon mode';
      }
    }
    final uid = text('vatUid');
    if (body['vatEnabled'] == true && uid == null) {
      errors['vatUid'] = 'A VAT number is required when VAT is turned on';
    } else if (uid != null && !RegExp(r'^CHE-\d{3}\.\d{3}\.\d{3} MWST$').hasMatch(uid)) {
      errors['vatUid'] = 'Not a valid VAT number, expected CHE-123.456.789 MWST';
    }
    if (errors.isNotEmpty) return FakeBackend._problem(400, 'Validation failed', errors);
    settings = {
      'mode': settings['mode'],
      for (final f in [
        'businessName',
        'street',
        'postalCode',
        'city',
        'phone',
        'email',
        'website',
        'iban',
        'accountHolder',
      ])
        f: text(f),
      'vatEnabled': body['vatEnabled'] == true,
      'vatUid': uid,
    };
    return FakeBackend._json(200, settings);
  }
}
