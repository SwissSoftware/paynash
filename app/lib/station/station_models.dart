import '../orders/order_models.dart';

/// A printer shown at this station, with its last known state and queue.
class StationPrinter {
  StationPrinter({
    required this.id,
    required this.name,
    required this.status,
    required this.waiting,
    required this.unclear,
    this.lastPrintedLabel,
    this.oldestWaitingSeconds,
  });

  factory StationPrinter.fromJson(Map<String, dynamic> json) => StationPrinter(
    id: json['id'] as int,
    name: json['name'] as String,
    status: json['status'] as String,
    waiting: json['waiting'] as int,
    unclear: json['unclear'] as int,
    lastPrintedLabel: json['lastPrintedLabel'] as String?,
    oldestWaitingSeconds: json['oldestWaitingSeconds'] as int?,
  );

  final int id;
  final String name;

  /// "ready", "offline", "paper end", "cover open", "paper near end", or
  /// "unknown" until the printer was checked for the first time.
  final String status;

  /// Tickets waiting or being printed.
  final int waiting;

  /// Unclear tickets that still need a decision.
  final int unclear;
  final String? lastPrintedLabel;

  /// How long the oldest waiting ticket has waited, by the server's clock.
  final int? oldestWaitingSeconds;

  /// Unknown is not an alarm: the server has not checked the printer yet.
  bool get hasProblem => status != 'ready' && status != 'unknown';
}

/// A ticket in the station list.
class StationTicket {
  StationTicket({
    required this.id,
    required this.orderId,
    required this.kind,
    required this.status,
    required this.tableLabel,
    required this.waiter,
    required this.lines,
    required this.needsResolution,
    required this.reprinted,
    required this.createdAt,
    this.label,
    this.waitingReason,
    this.error,
  });

  factory StationTicket.fromJson(Map<String, dynamic> json) => StationTicket(
    id: json['id'] as int,
    orderId: json['orderId'] as int,
    kind: TicketKind.values.byName(json['kind'] as String),
    status: TicketStatus.values.byName(json['status'] as String),
    tableLabel: json['tableLabel'] as String,
    waiter: json['waiter'] as String,
    lines: json['lines'] as int,
    needsResolution: json['needsResolution'] as bool,
    reprinted: json['reprinted'] as bool? ?? false,
    createdAt: DateTime.parse(json['createdAt'] as String),
    label: json['label'] as String?,
    waitingReason: json['waitingReason'] as String?,
    error: json['error'] as String?,
  );

  final int id;
  final int orderId;
  final TicketKind kind;
  final TicketStatus status;
  final String tableLabel;
  final String waiter;
  final int lines;

  /// Unclear and not yet resolved as "was printed" or "reprint".
  final bool needsResolution;
  final bool reprinted;
  final DateTime createdAt;

  /// The running ticket number, e.g. "BAR 0142".
  final String? label;
  final String? waitingReason;
  final String? error;

  bool get inProgress => status == TicketStatus.pending || status == TicketStatus.printing;
}
