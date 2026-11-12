import '../api/api_client.dart';
import '../menu/menu_models.dart';

class PrinterData {
  PrinterData({
    required this.id,
    required this.name,
    required this.address,
    required this.deviceId,
    required this.type,
    required this.active,
    this.status = 'unknown',
    this.stations = const [],
  });

  factory PrinterData.fromJson(Map<String, dynamic> json) => PrinterData(
        id: json['id'] as int,
        name: json['name'] as String,
        address: json['address'] as String,
        deviceId: json['deviceId'] as String,
        type: ItemType.values.byName(json['type'] as String),
        active: json['active'] as bool,
        status: json['status'] as String? ?? 'unknown',
        stations: [
          for (final s in json['stations'] as List? ?? const [])
            (
              name: s['name'] as String,
              lastSeenAt: s['lastSeenAt'] == null ? null : DateTime.parse(s['lastSeenAt'] as String),
            ),
        ],
      );

  final int id;
  final String name;
  final String address;
  final String deviceId;

  /// Food goes to the kitchen printer, drinks to the bar printer.
  final ItemType type;
  final bool active;

  /// The printer's last known state: ready, offline, paper end, ... or unknown.
  final String status;

  /// Stations showing this printer and when each last asked for its tickets,
  /// so a station tablet that went to sleep is noticed.
  final List<({String name, DateTime? lastSeenAt})> stations;
}

class PrintersApi {
  PrintersApi(this._api);

  final ApiClient _api;

  Future<List<PrinterData>> list() async => [
        for (final json in await _api.get('/admin/printers') as List)
          PrinterData.fromJson(json as Map<String, dynamic>),
      ];

  Map<String, dynamic> _body(String name, String address, String deviceId, ItemType type,
          bool active) =>
      {
        'name': name,
        'address': address,
        'deviceId': deviceId,
        'type': type.name,
        'active': active,
      };

  Future<void> create(
          {required String name,
          required String address,
          required String deviceId,
          required ItemType type,
          required bool active}) =>
      _api.post('/admin/printers', _body(name, address, deviceId, type, active));

  Future<void> update(int id,
          {required String name,
          required String address,
          required String deviceId,
          required ItemType type,
          required bool active}) =>
      _api.put('/admin/printers/$id', _body(name, address, deviceId, type, active));

  /// Prints a test ticket now. Returns null on success, else the printer's
  /// error.
  Future<String?> testPrint(int id) async {
    final result = await _api.post('/admin/printers/$id/test') as Map<String, dynamic>;
    return result['success'] == true ? null : result['error'] as String;
  }
}
