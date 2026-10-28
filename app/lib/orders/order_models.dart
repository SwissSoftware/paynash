import '../menu/menu_models.dart';

/// One row of the open orders overview.
class OpenOrderSummary {
  OpenOrderSummary({
    required this.id,
    required this.tableLabel,
    required this.waiter,
    required this.total,
    required this.ageMinutes,
    required this.unsentLines,
  });

  factory OpenOrderSummary.fromJson(Map<String, dynamic> json) => OpenOrderSummary(
    id: json['id'] as int,
    tableLabel: json['tableLabel'] as String,
    waiter: json['waiter'] as String,
    total: json['total'] as String,
    ageMinutes: json['ageMinutes'] as int,
    unsentLines: json['unsentLines'] as int,
  );

  final int id;
  final String tableLabel;
  final String waiter;
  final String total;
  final int ageMinutes;
  final int unsentLines;
}

enum OrderStatus { open, paid, voided }

enum LineStatus { unsent, sent, cancelled }

class OrderLineData {
  OrderLineData({
    required this.id,
    required this.menuItemId,
    required this.name,
    required this.type,
    required this.unitPrice,
    required this.qty,
    required this.status,
    required this.lineTotal,
    required this.version,
    this.note,
    this.roundNumber,
    this.cancelReason,
  });

  factory OrderLineData.fromJson(Map<String, dynamic> json) => OrderLineData(
    id: json['id'] as int,
    menuItemId: json['menuItemId'] as int,
    name: json['name'] as String,
    type: ItemType.values.byName(json['type'] as String),
    unitPrice: json['unitPrice'] as String,
    qty: json['qty'] as int,
    note: json['note'] as String?,
    status: LineStatus.values.byName(json['status'] as String),
    roundNumber: json['roundNumber'] as int?,
    lineTotal: json['lineTotal'] as String,
    version: json['version'] as int,
    cancelReason: json['cancelReason'] as String?,
  );

  final int id;
  final int menuItemId;
  final String name;
  final ItemType type;
  final String unitPrice;
  final int qty;
  final String? note;
  final LineStatus status;
  final int? roundNumber;
  final String lineTotal;

  /// Sent back when changing the line, so a change made on another device
  /// meanwhile is not overwritten.
  final int version;
  final String? cancelReason;
}

class OrderData {
  OrderData({
    required this.id,
    required this.tableLabel,
    required this.status,
    required this.waiter,
    required this.version,
    required this.total,
    required this.hasUnsentLines,
    required this.lines,
  });

  factory OrderData.fromJson(Map<String, dynamic> json) => OrderData(
    id: json['id'] as int,
    tableLabel: json['tableLabel'] as String,
    status: OrderStatus.values.byName(json['status'] as String),
    waiter: json['waiter'] as String,
    version: json['version'] as int,
    total: json['total'] as String,
    hasUnsentLines: json['hasUnsentLines'] as bool,
    lines: [
      for (final l in json['lines'] as List) OrderLineData.fromJson(l as Map<String, dynamic>),
    ],
  );

  final int id;
  final String tableLabel;
  final OrderStatus status;
  final String waiter;
  final int version;
  final String total;
  final bool hasUnsentLines;
  final List<OrderLineData> lines;

  int get unsentCount => lines.where((l) => l.status == LineStatus.unsent).length;
}
