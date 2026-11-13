import '../api/api_client.dart';
import '../export/file_saver.dart';
import '../invoices/invoice_api.dart';

class ReportSummary {
  ReportSummary(this.json);

  final Map<String, dynamic> json;

  String get revenue => json['revenue'] as String;
  int get invoiceCount => json['invoiceCount'] as int;
  String get paid => json['paid'] as String;
  String get open => json['open'] as String;

  /// cash, card, twint, bank_transfer with their amounts.
  List<({String method, String amount})> get byMethod => [
    for (final m in json['byMethod'] as List)
      (method: m['method'] as String, amount: m['amount'] as String),
  ];

  List<({String rate, String gross, String net, String vat})> get vat => [
    for (final v in json['vat'] as List)
      (
        rate: v['rate'] as String,
        gross: v['gross'] as String,
        net: v['net'] as String,
        vat: v['vat'] as String,
      ),
  ];
}

/// The sales report of a period (spec sales-reporting).
class ReportData {
  ReportData(this.json);

  final Map<String, dynamic> json;

  String get from => json['from'] as String;
  String get to => json['to'] as String;
  ReportSummary get summary => ReportSummary(json['summary'] as Map<String, dynamic>);

  List<({String date, String revenue, int invoiceCount})> get days => [
    for (final d in json['days'] as List)
      (
        date: d['date'] as String,
        revenue: d['revenue'] as String,
        invoiceCount: d['invoiceCount'] as int,
      ),
  ];

  List<({String name, int quantity, String revenue})> get services => [
    for (final s in json['services'] as List)
      (name: s['name'] as String, quantity: s['quantity'] as int, revenue: s['revenue'] as String),
  ];

  List<({int number, String name, int invoiceCount, String revenue})> get customers => [
    for (final c in json['customers'] as List)
      (
        number: c['number'] as int,
        name: c['name'] as String,
        invoiceCount: c['invoiceCount'] as int,
        revenue: c['revenue'] as String,
      ),
  ];
}

class ReportApi {
  ReportApi(this.api);

  final ApiClient api;

  Future<ReportData> load(DateTime from, DateTime to) async => ReportData(
    await api.get('/admin/reports/sales?from=${isoDate(from)}&to=${isoDate(to)}')
        as Map<String, dynamic>,
  );

  /// Downloads the report as Excel and saves it. Returns the file name.
  Future<String> downloadExcel(DateTime from, DateTime to) async {
    final file = await api.getBytes(
      '/admin/reports/sales.xlsx?from=${isoDate(from)}&to=${isoDate(to)}',
    );
    final name = file.filename ?? 'Rapport_${isoDate(from)}_${isoDate(to)}.xlsx';
    await FileSaver.instance.save(
      name,
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      file.bytes,
    );
    return name;
  }
}
