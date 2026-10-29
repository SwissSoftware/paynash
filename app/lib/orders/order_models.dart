import '../menu/menu_models.dart';

/// One row of the order history.
class OrderSummary {
  OrderSummary({
    required this.id,
    required this.tableLabel,
    required this.status,
    required this.waiter,
    required this.sentAt,
    required this.total,
    this.printStatus,
    this.printError,
  });

  factory OrderSummary.fromJson(Map<String, dynamic> json) => OrderSummary(
    id: json['id'] as int,
    tableLabel: json['tableLabel'] as String,
    status: OrderStatus.values.byName(json['status'] as String),
    waiter: json['waiter'] as String,
    sentAt: DateTime.parse(json['sentAt'] as String),
    total: json['total'] as String,
    printStatus: switch (json['printStatus'] as String?) {
      final s? => TicketStatus.values.byName(s),
      null => null,
    },
    printError: json['printError'] as String?,
  );

  final int id;
  final String tableLabel;
  final OrderStatus status;
  final String waiter;
  final DateTime sentAt;
  final String total;

  /// The state of the order's tickets, ignoring tickets that were reprinted:
  /// failed if any failed (with [printError]), else pending if any is still
  /// printing. Null when the order has no tickets.
  final TicketStatus? printStatus;
  final String? printError;
}

enum OrderStatus { sent, voided }

enum TicketStatus { pending, printed, failed }

enum TicketKind { normal, storno, reprint }

/// A production ticket of an order (kitchen or bar).
class TicketData {
  TicketData({
    required this.id,
    required this.kind,
    required this.type,
    required this.status,
    required this.reprinted,
    this.printer,
    this.error,
  });

  factory TicketData.fromJson(Map<String, dynamic> json) => TicketData(
    id: json['id'] as int,
    kind: TicketKind.values.byName(json['kind'] as String),
    type: ItemType.values.byName(json['type'] as String),
    printer: json['printer'] as String?,
    status: TicketStatus.values.byName(json['status'] as String),
    error: json['error'] as String?,
    reprinted: json['reprinted'] as bool? ?? false,
  );

  final int id;
  final TicketKind kind;
  final ItemType type;

  /// Null when no printer was configured for [type].
  final String? printer;
  final TicketStatus status;
  final String? error;

  /// A later reprint replaces this ticket.
  final bool reprinted;

  /// Failed and not yet replaced by a reprint: staff need to act.
  bool get needsAttention => status == TicketStatus.failed && !reprinted;
}

enum LineStatus { sent, cancelled }

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
    this.note,
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
    lineTotal: json['lineTotal'] as String,
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
  final String lineTotal;
  final String? cancelReason;
}

/// A sent order. It is final; only an admin can cancel lines or void it.
class OrderData {
  OrderData({
    required this.id,
    required this.tableLabel,
    required this.status,
    required this.waiter,
    required this.sentAt,
    required this.version,
    required this.total,
    required this.lines,
    this.tickets = const [],
    this.voidReason,
  });

  factory OrderData.fromJson(Map<String, dynamic> json) => OrderData(
    id: json['id'] as int,
    tableLabel: json['tableLabel'] as String,
    status: OrderStatus.values.byName(json['status'] as String),
    waiter: json['waiter'] as String,
    sentAt: DateTime.parse(json['sentAt'] as String),
    version: json['version'] as int,
    total: json['total'] as String,
    voidReason: json['voidReason'] as String?,
    lines: [
      for (final l in json['lines'] as List) OrderLineData.fromJson(l as Map<String, dynamic>),
    ],
    tickets: [
      for (final t in json['tickets'] as List? ?? const [])
        TicketData.fromJson(t as Map<String, dynamic>),
    ],
  );

  final int id;
  final String tableLabel;
  final OrderStatus status;
  final String waiter;
  final DateTime sentAt;

  /// Sent back when voiding, so cancellations made meanwhile are not overlooked.
  final int version;
  final String total;
  final List<OrderLineData> lines;
  final List<TicketData> tickets;
  final String? voidReason;
}

/// A line of the order being built on this device. Nothing is stored on the
/// server until the whole order is sent.
class DraftLine {
  DraftLine(this.item, {this.qty = 1, this.note});

  final MenuItemData item;
  int qty;
  String? note;

  int get totalRappen => qty * rappen(item.price);

  Map<String, dynamic> toJson() => {'menuItemId': item.id, 'qty': qty, 'note': note};
}

/// "4.50" → 450.
int rappen(String chf) {
  final [francs, cents] = chf.contains('.') ? chf.split('.') : [chf, '0'];
  return int.parse(francs) * 100 + int.parse(cents.padRight(2, '0'));
}

/// 450 → "4.50".
String chf(int rappen) => '${rappen ~/ 100}.${(rappen % 100).toString().padLeft(2, '0')}';
