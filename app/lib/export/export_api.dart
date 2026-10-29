import '../api/api_client.dart';
import 'file_saver.dart';

enum ExportStatus { sent, voided, all }

/// The order export as CSV (admin only).
class ExportApi {
  ExportApi(this._api);

  final ApiClient _api;

  /// Downloads the order lines sent from [from] to [to] (inclusive local
  /// dates) and saves the file. Returns its name.
  Future<String> downloadOrders(DateTime from, DateTime to, ExportStatus status) async {
    final file = await _api.getBytes(
      '/exports/orders.csv?from=${_date(from)}&to=${_date(to)}&status=${status.name}',
    );
    final name = file.filename ?? 'orders_${_date(from)}_${_date(to)}.csv';
    await FileSaver.instance.save(name, 'text/csv', file.bytes);
    return name;
  }

  static String _date(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}
