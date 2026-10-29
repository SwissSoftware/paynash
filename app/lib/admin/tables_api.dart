import '../api/api_client.dart';

/// A table of the restaurant. [number] is what orders store as their table.
class TableData {
  TableData({required this.id, required this.number, this.name, this.active = true});

  factory TableData.fromJson(Map<String, dynamic> json) => TableData(
        id: json['id'] as int,
        number: json['number'] as String,
        name: json['name'] as String?,
        active: json['active'] as bool? ?? true,
      );

  final int id;
  final String number;
  final String? name;
  final bool active;
}

class TablesApi {
  TablesApi(this._api);

  final ApiClient _api;

  /// Active tables for taking orders, sorted by number ("2" before "10").
  Future<List<TableData>> active() async => [
        for (final json in await _api.get('/tables') as List)
          TableData.fromJson(json as Map<String, dynamic>),
      ];

  /// All tables, including inactive ones (admin).
  Future<List<TableData>> list() async => [
        for (final json in await _api.get('/admin/tables') as List)
          TableData.fromJson(json as Map<String, dynamic>),
      ];

  Future<void> create({required String number, String? name}) =>
      _api.post('/admin/tables', {'number': number, 'name': name});

  Future<void> update(int id,
          {required String number, String? name, required bool active}) =>
      _api.put('/admin/tables/$id', {'number': number, 'name': name, 'active': active});

  Future<void> delete(int id) => _api.delete('/admin/tables/$id');
}
