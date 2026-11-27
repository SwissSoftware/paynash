import '../api/api_client.dart';

/// A menu category as shown with a printer.
typedef PrinterCategory = ({int id, String name});

PrinterCategory _category(dynamic json) => (
  id: (json as Map<String, dynamic>)['id'] as int,
  name: json['name'] as String,
);

class PrinterData {
  PrinterData({
    required this.id,
    required this.name,
    required this.address,
    required this.deviceId,
    required this.active,
    this.printsTickets = false,
    this.printsReceipts = false,
    this.categories = const [],
    this.status = 'unknown',
    this.stations = const [],
  });

  factory PrinterData.fromJson(Map<String, dynamic> json) => PrinterData(
    id: json['id'] as int,
    name: json['name'] as String,
    address: json['address'] as String,
    deviceId: json['deviceId'] as String,
    printsTickets: json['printsTickets'] as bool? ?? false,
    printsReceipts: json['printsReceipts'] as bool? ?? false,
    categories: [
      for (final c in json['categories'] as List? ?? const []) _category(c),
    ],
    active: json['active'] as bool,
    status: json['status'] as String? ?? 'unknown',
    stations: [
      for (final s in json['stations'] as List? ?? const [])
        (
          name: s['name'] as String,
          lastSeenAt: s['lastSeenAt'] == null
              ? null
              : DateTime.parse(s['lastSeenAt'] as String),
        ),
    ],
  );

  final int id;
  final String name;
  final String address;
  final String deviceId;

  /// Prints the lines of [categories] (change printer-category-routing).
  final bool printsTickets;

  /// Prints invoice receipts.
  final bool printsReceipts;

  /// The categories whose lines this printer prints while [printsTickets] is on.
  final List<PrinterCategory> categories;
  final bool active;

  /// The printer's last known state: ready, offline, paper end, ... or unknown.
  final String status;

  /// Stations showing this printer and when each last asked for its tickets,
  /// so a station tablet that went to sleep is noticed.
  final List<({String name, DateTime? lastSeenAt})> stations;
}

/// The printers, and the categories that no active printer with tickets on
/// prints (information only: printing is opt-in per category).
typedef PrinterList = ({
  List<PrinterData> printers,
  List<PrinterCategory> unprintedCategories,
});

class PrintersApi {
  PrintersApi(this._api);

  final ApiClient _api;

  Future<PrinterList> list() async {
    final json = await _api.get('/admin/printers') as Map<String, dynamic>;
    return (
      printers: [
        for (final p in json['printers'] as List)
          PrinterData.fromJson(p as Map<String, dynamic>),
      ],
      unprintedCategories: [
        for (final c in json['unprintedCategories'] as List? ?? const [])
          _category(c),
      ],
    );
  }

  /// All menu categories, to choose which ones a printer prints.
  Future<List<PrinterCategory>> categories() async => [
    for (final c in await _api.get('/admin/categories') as List) _category(c),
  ];

  Map<String, dynamic> _body(
    String name,
    String address,
    String deviceId,
    bool printsTickets,
    bool printsReceipts,
    List<int> categoryIds,
    bool active,
  ) => {
    'name': name,
    'address': address,
    'deviceId': deviceId,
    'printsTickets': printsTickets,
    'printsReceipts': printsReceipts,
    'categoryIds': categoryIds,
    'active': active,
  };

  Future<void> create({
    required String name,
    required String address,
    required String deviceId,
    required bool printsTickets,
    required bool printsReceipts,
    required List<int> categoryIds,
    required bool active,
  }) => _api.post(
    '/admin/printers',
    _body(
      name,
      address,
      deviceId,
      printsTickets,
      printsReceipts,
      categoryIds,
      active,
    ),
  );

  Future<void> update(
    int id, {
    required String name,
    required String address,
    required String deviceId,
    required bool printsTickets,
    required bool printsReceipts,
    required List<int> categoryIds,
    required bool active,
  }) => _api.put(
    '/admin/printers/$id',
    _body(
      name,
      address,
      deviceId,
      printsTickets,
      printsReceipts,
      categoryIds,
      active,
    ),
  );

  /// Prints a test ticket now. Returns null on success, else the printer's
  /// error.
  Future<String?> testPrint(int id) async {
    final result =
        await _api.post('/admin/printers/$id/test') as Map<String, dynamic>;
    return result['success'] == true ? null : result['error'] as String;
  }
}
