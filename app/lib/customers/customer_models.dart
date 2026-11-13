/// A salon customer (spec customer-management). [name] is "Deleted customer"
/// once the customer was anonymized.
class CustomerData {
  CustomerData({
    required this.id,
    required this.number,
    required this.name,
    this.firstName,
    this.lastName,
    this.phone,
    this.email,
    this.street,
    this.postalCode,
    this.city,
    this.note,
    this.active = true,
    this.anonymized = false,
  });

  factory CustomerData.fromJson(Map<String, dynamic> json) => CustomerData(
    id: json['id'] as int,
    number: json['number'] as int,
    name: json['name'] as String,
    firstName: json['firstName'] as String?,
    lastName: json['lastName'] as String?,
    phone: json['phone'] as String?,
    email: json['email'] as String?,
    street: json['street'] as String?,
    postalCode: json['postalCode'] as String?,
    city: json['city'] as String?,
    note: json['note'] as String?,
    active: json['active'] as bool? ?? true,
    anonymized: json['anonymized'] as bool? ?? false,
  );

  final int id;
  final int number;
  final String name;
  final String? firstName;
  final String? lastName;
  final String? phone;
  final String? email;
  final String? street;
  final String? postalCode;
  final String? city;
  final String? note;
  final bool active;
  final bool anonymized;

  /// "079 555 12 34 · laura@example.ch", or null without contact details.
  String? get contact {
    final parts = [?phone, ?email];
    return parts.isEmpty ? null : parts.join(' · ');
  }
}

/// One invoice in a customer's history. [status] is open, paid, or cancelled.
class CustomerInvoice {
  CustomerInvoice({
    required this.id,
    required this.number,
    required this.date,
    required this.total,
    required this.status,
  });

  factory CustomerInvoice.fromJson(Map<String, dynamic> json) => CustomerInvoice(
    id: json['id'] as int,
    number: json['number'] as String,
    date: json['date'] as String,
    total: json['total'] as String,
    status: json['status'] as String,
  );

  final int id;
  final String number;

  /// ISO date, e.g. "2026-11-13".
  final String date;
  final String total;
  final String status;
}

class CustomerDetail {
  CustomerDetail({required this.customer, required this.invoices, required this.revenue});

  factory CustomerDetail.fromJson(Map<String, dynamic> json) => CustomerDetail(
    customer: CustomerData.fromJson(json['customer'] as Map<String, dynamic>),
    invoices: [
      for (final i in json['invoices'] as List) CustomerInvoice.fromJson(i as Map<String, dynamic>),
    ],
    revenue: json['revenue'] as String,
  );

  final CustomerData customer;

  /// Newest first.
  final List<CustomerInvoice> invoices;

  /// Total of the invoices that are not cancelled.
  final String revenue;
}

/// The fields of the customer form.
class CustomerInput {
  CustomerInput({
    this.firstName = '',
    this.lastName = '',
    this.phone = '',
    this.email = '',
    this.street = '',
    this.postalCode = '',
    this.city = '',
    this.note = '',
  });

  final String firstName;
  final String lastName;
  final String phone;
  final String email;
  final String street;
  final String postalCode;
  final String city;
  final String note;

  Map<String, dynamic> toJson() => {
    'firstName': firstName,
    'lastName': lastName,
    'phone': phone,
    'email': email,
    'street': street,
    'postalCode': postalCode,
    'city': city,
    'note': note,
  };
}

/// "13.11.2026" from "2026-11-13".
String swissDate(String iso) {
  final [y, m, d] = iso.split('-');
  return '$d.$m.$y';
}
