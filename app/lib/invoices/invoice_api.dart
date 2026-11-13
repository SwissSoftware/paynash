import '../api/api_client.dart';
import '../export/file_saver.dart';
import 'invoice_models.dart';

class InvoiceApi {
  InvoiceApi(this.api);

  final ApiClient api;

  /// Invoices of the period (default: the current month), newest first.
  Future<List<InvoiceSummary>> list({
    DateTime? from,
    DateTime? to,
    InvoiceStatus? status,
    String query = '',
  }) async {
    final params = {
      'from': ?(from == null ? null : isoDate(from)),
      'to': ?(to == null ? null : isoDate(to)),
      'status': ?status?.name,
      if (query.trim().isNotEmpty) 'q': query.trim(),
    };
    final qs = params.isEmpty ? '' : '?${Uri(queryParameters: params).query}';
    return [
      for (final i in await api.get('/invoices$qs') as List)
        InvoiceSummary.fromJson(i as Map<String, dynamic>),
    ];
  }

  Future<InvoiceData> get(int id) async =>
      InvoiceData.fromJson(await api.get('/invoices/$id') as Map<String, dynamic>);

  /// Issues the invoice of a confirmed order. Pass the same [idempotencyKey]
  /// when retrying, so the order is never invoiced twice.
  Future<InvoiceData> issue(int orderId, String idempotencyKey) async => InvoiceData.fromJson(
    await api.post('/orders/$orderId/invoice', null, idempotencyKey) as Map<String, dynamic>,
  );

  Future<InvoiceData> cancel(int id, String reason) async => InvoiceData.fromJson(
    await api.post('/admin/invoices/$id/cancel', {'reason': reason}, newIdempotencyKey())
        as Map<String, dynamic>,
  );

  Future<InvoiceData> pay(int id, PaymentMethod method, DateTime paidOn) async =>
      InvoiceData.fromJson(
        await api.post('/invoices/$id/payment', {
              'method': method.json,
              'paidOn': isoDate(paidOn),
            }, newIdempotencyKey())
            as Map<String, dynamic>,
      );

  Future<InvoiceData> undoPayment(int id, String reason) async => InvoiceData.fromJson(
    await api.post('/admin/invoices/$id/payment/undo', {'reason': reason}, newIdempotencyKey())
        as Map<String, dynamic>,
  );

  /// Prints the receipt again, marked "KOPIE".
  Future<void> printCopy(int id) => api.post('/invoices/$id/receipt', null, newIdempotencyKey());

  /// Downloads the A4 PDF and saves it. Returns the file name.
  Future<String> downloadPdf(InvoiceData invoice) async {
    final file = await api.getBytes('/invoices/${invoice.id}/pdf');
    final name = file.filename ?? 'Rechnung-${invoice.number}.pdf';
    await FileSaver.instance.save(name, 'application/pdf', file.bytes);
    return name;
  }
}

/// "2026-11-13".
String isoDate(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-'
    '${d.day.toString().padLeft(2, '0')}';
