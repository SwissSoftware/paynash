/// How the installation runs (spec venue-mode).
enum VenueMode { event, salon }

/// Installation mode and business details (GET /settings).
class AppSettings {
  AppSettings({
    required this.mode,
    this.businessName,
    this.street,
    this.postalCode,
    this.city,
    this.phone,
    this.email,
    this.website,
    this.iban,
    this.accountHolder,
    this.vatEnabled = false,
    this.vatUid,
  });

  factory AppSettings.fromJson(Map<String, dynamic> json) => AppSettings(
    mode: VenueMode.values.byName(json['mode'] as String),
    businessName: json['businessName'] as String?,
    street: json['street'] as String?,
    postalCode: json['postalCode'] as String?,
    city: json['city'] as String?,
    phone: json['phone'] as String?,
    email: json['email'] as String?,
    website: json['website'] as String?,
    iban: json['iban'] as String?,
    accountHolder: json['accountHolder'] as String?,
    vatEnabled: json['vatEnabled'] as bool? ?? false,
    vatUid: json['vatUid'] as String?,
  );

  final VenueMode mode;
  final String? businessName;
  final String? street;
  final String? postalCode;
  final String? city;
  final String? phone;
  final String? email;
  final String? website;
  final String? iban;
  final String? accountHolder;
  final bool vatEnabled;
  final String? vatUid;

  bool get isSalon => mode == VenueMode.salon;

  Terms get terms => Terms(mode);
}

/// The words the apps use per mode (spec venue-mode, "Salon terms"). The
/// stored roles and states stay the same in both modes.
class Terms {
  const Terms(this.mode);

  final VenueMode mode;

  bool get _salon => mode == VenueMode.salon;

  /// The `waiter` role.
  String get waiter => _salon ? 'Staff' : 'Waiter';

  /// Menu items.
  String get items => _salon ? 'Services' : 'Menu';

  /// One menu item.
  String get item => _salon ? 'Service' : 'Item';

  /// Sending an order.
  String get send => _salon ? 'Confirm' : 'Send';
}
