/// open, paid, or cancelled.
enum InvoiceStatus { open, paid, cancelled }

/// Payment methods (spec invoicing, "Payment status").
enum PaymentMethod { cash, card, twint, bankTransfer }

extension PaymentMethodJson on PaymentMethod {
  String get json => this == PaymentMethod.bankTransfer ? 'bank_transfer' : name;

  String get label => switch (this) {
    PaymentMethod.cash => 'Cash',
    PaymentMethod.card => 'Card',
    PaymentMethod.twint => 'TWINT',
    PaymentMethod.bankTransfer => 'Bank transfer',
  };

  static PaymentMethod parse(String value) =>
      value == 'bank_transfer' ? PaymentMethod.bankTransfer : PaymentMethod.values.byName(value);
}

/// One row of the invoice list.
class InvoiceSummary {
  InvoiceSummary({
    required this.id,
    required this.number,
    required this.date,
    required this.customerNumber,
    required this.customerName,
    required this.total,
    required this.status,
  });

  factory InvoiceSummary.fromJson(Map<String, dynamic> json) => InvoiceSummary(
    id: json['id'] as int,
    number: json['number'] as String,
    date: json['date'] as String,
    customerNumber: json['customerNumber'] as int?,
    customerName: json['customerName'] as String?,
    total: json['total'] as String,
    status: InvoiceStatus.values.byName(json['status'] as String),
  );

  final int id;
  final String number;
  final String date;

  /// Null for an invoice without customer (change invoices-without-customer).
  final int? customerNumber;
  final String? customerName;
  final String total;
  final InvoiceStatus status;

  /// The customer's name, or "Ohne Kunde".
  String get customerLabel => customerName ?? withoutCustomer;
}

/// Shown instead of a customer for invoices without customer (spec invoicing).
const withoutCustomer = 'Ohne Kunde';

class InvoiceLine {
  InvoiceLine(this.qty, this.name, this.unitPrice, this.vatRate, this.amount, this.note);

  factory InvoiceLine.fromJson(Map<String, dynamic> json) => InvoiceLine(
    json['qty'] as int,
    json['name'] as String,
    json['unitPrice'] as String,
    json['vatRate'] as String,
    json['amount'] as String,
    json['note'] as String?,
  );

  final int qty;
  final String name;
  final String unitPrice;
  final String vatRate;
  final String amount;
  final String? note;
}

class VatRow {
  VatRow(this.rate, this.gross, this.net, this.vat);

  factory VatRow.fromJson(Map<String, dynamic> json) => VatRow(
    json['rate'] as String,
    json['gross'] as String,
    json['net'] as String,
    json['vat'] as String,
  );

  final String rate;
  final String gross;
  final String net;
  final String vat;
}

class PaymentData {
  PaymentData({
    required this.method,
    required this.paidOn,
    required this.recordedBy,
    this.removedBy,
    this.removeReason,
  });

  factory PaymentData.fromJson(Map<String, dynamic> json) => PaymentData(
    method: PaymentMethodJson.parse(json['method'] as String),
    paidOn: json['paidOn'] as String,
    recordedBy: json['recordedBy'] as String,
    removedBy: json['removedBy'] as String?,
    removeReason: json['removeReason'] as String?,
  );

  final PaymentMethod method;
  final String paidOn;
  final String recordedBy;
  final String? removedBy;
  final String? removeReason;

  bool get removed => removedBy != null;
}

/// A receipt print job of an invoice, on one printer.
class ReceiptStatus {
  ReceiptStatus({
    required this.status,
    this.printer,
    this.kind,
    this.error,
    this.waitingReason,
    this.label,
  });

  factory ReceiptStatus.fromJson(Map<String, dynamic> json) => ReceiptStatus(
    status: json['status'] as String,
    printer: json['printer'] as String?,
    kind: json['kind'] as String?,
    error: json['error'] as String?,
    waitingReason: json['waitingReason'] as String?,
    label: json['label'] as String?,
  );

  /// pending, printing, printed, unclear, or failed.
  final String status;
  final String? printer;
  final String? kind;
  final String? error;
  final String? waitingReason;
  final String? label;

  bool get inProgress => status == 'pending' || status == 'printing';

  bool get needsAttention => status == 'unclear' || status == 'failed';

  /// The status, prefixed with the printer when an invoice has several.
  String textOn({required bool withPrinter}) =>
      withPrinter && printer != null ? '$printer: $text' : text;

  String get text => switch (status) {
    'printed' => 'Receipt printed',
    'pending' => waitingReason == null ? 'Receipt waiting' : 'Receipt waiting: $waitingReason',
    'printing' => 'Receipt printing',
    'unclear' => 'Receipt unclear: check the receipt and print a copy if needed',
    _ => 'Receipt failed: ${error ?? 'unknown error'}',
  };
}

/// An issued invoice; final, everything as it was when issued.
class InvoiceData {
  InvoiceData({
    required this.id,
    required this.number,
    required this.orderId,
    required this.date,
    required this.status,
    required this.businessName,
    required this.customerNumber,
    required this.customerName,
    required this.customerAddress,
    required this.lines,
    required this.total,
    required this.vatEnabled,
    required this.vat,
    required this.paymentHistory,
    this.vatUid,
    this.cancelReason,
    this.cancelledBy,
    this.payment,
    this.receipt,
    this.receipts = const [],
  });

  factory InvoiceData.fromJson(Map<String, dynamic> json) {
    final c = json['customer'] as Map<String, dynamic>?;
    final place = [c?['postalCode'], c?['city']].whereType<String>().join(' ');
    return InvoiceData(
      id: json['id'] as int,
      number: json['number'] as String,
      orderId: json['orderId'] as int,
      date: json['date'] as String,
      status: InvoiceStatus.values.byName(json['status'] as String),
      businessName: (json['business'] as Map<String, dynamic>)['name'] as String,
      customerNumber: c?['number'] as int?,
      customerName: c?['name'] as String?,
      customerAddress: [
        c?['street'] as String?,
        if (place.isNotEmpty) place,
      ].whereType<String>().toList(),
      lines: [
        for (final l in json['lines'] as List) InvoiceLine.fromJson(l as Map<String, dynamic>),
      ],
      total: json['total'] as String,
      vatEnabled: json['vatEnabled'] as bool,
      vatUid: json['vatUid'] as String?,
      vat: [for (final v in json['vat'] as List) VatRow.fromJson(v as Map<String, dynamic>)],
      cancelReason: json['cancelReason'] as String?,
      cancelledBy: json['cancelledBy'] as String?,
      payment: switch (json['payment']) {
        final Map<String, dynamic> p => PaymentData.fromJson(p),
        _ => null,
      },
      paymentHistory: [
        for (final p in json['paymentHistory'] as List? ?? const [])
          PaymentData.fromJson(p as Map<String, dynamic>),
      ],
      receipt: switch (json['receipt']) {
        final Map<String, dynamic> r => ReceiptStatus.fromJson(r),
        _ => null,
      },
      receipts: [
        for (final r in json['receipts'] as List? ?? const [])
          ReceiptStatus.fromJson(r as Map<String, dynamic>),
      ],
    );
  }

  final int id;
  final String number;
  final int orderId;
  final String date;
  final InvoiceStatus status;
  final String businessName;

  /// Null for an invoice without customer (change invoices-without-customer).
  final int? customerNumber;
  final String? customerName;
  final List<String> customerAddress;

  bool get hasCustomer => customerName != null;

  /// The customer's name, or "Ohne Kunde".
  String get customerLabel => customerName ?? withoutCustomer;
  final List<InvoiceLine> lines;
  final String total;
  final bool vatEnabled;
  final String? vatUid;
  final List<VatRow> vat;
  final String? cancelReason;
  final String? cancelledBy;
  final PaymentData? payment;
  final List<PaymentData> paymentHistory;

  /// The latest receipt job overall.
  final ReceiptStatus? receipt;

  /// The latest receipt job of each printer with receipts on; empty when no
  /// printer prints receipts (change printer-category-routing).
  final List<ReceiptStatus> receipts;

  /// No receipt came out: no printer has receipts on, or all of them failed.
  bool get noReceiptPrinted => receipts.every((r) => r.status == 'failed');
}
