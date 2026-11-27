/// A function of the installation that admins switch on or off (spec
/// business-features, "Feature set"). [key] is the name in the API.
enum Feature {
  tables('tables', 'Tables'),
  stations('stations', 'Stations'),
  customers('customers', 'Customers'),
  invoices('invoices', 'Invoices'),
  salesReport('sales-report', 'Sales report');

  const Feature(this.key, this.label);

  final String key;
  final String label;

  /// The feature this one cannot work without (spec business-features,
  /// "Feature dependencies").
  Feature? get requires => switch (this) {
    Feature.invoices => Feature.customers,
    Feature.salesReport => Feature.invoices,
    _ => null,
  };
}

/// Starting combinations of features and terms (spec business-features,
/// "Business templates"). [custom] is shown after manual changes.
enum BusinessTemplate {
  event('Event'),
  restaurant('Restaurant'),
  salon('Salon'),
  counter('Counter sales'),
  custom('Custom');

  const BusinessTemplate(this.label);

  final String label;
}

/// Features, template, terms, and business details (GET /settings).
class AppSettings {
  AppSettings({
    required this.features,
    this.template = BusinessTemplate.custom,
    this.terms = Terms.standard,
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

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    final features = json['features'] as Map<String, dynamic>? ?? const {};
    return AppSettings(
      features: {
        for (final f in Feature.values)
          if (features[f.key] == true) f,
      },
      template: BusinessTemplate.values.byName(json['template'] as String? ?? 'custom'),
      terms: Terms.fromJson(json['terms'] as Map<String, dynamic>?),
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
  }

  final Set<Feature> features;
  final BusinessTemplate template;
  final Terms terms;
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

  bool has(Feature feature) => features.contains(feature);
}

/// The configurable words (spec business-features, "Configurable terms"). The
/// stored roles and states never change with them.
class Terms {
  const Terms({required this.waiter, required this.items, required this.send});

  factory Terms.fromJson(Map<String, dynamic>? json) => json == null
      ? standard
      : Terms(
          waiter: json['waiter'] as String? ?? standard.waiter,
          items: json['items'] as String? ?? standard.items,
          send: json['send'] as String? ?? standard.send,
        );

  static const standard = Terms(waiter: 'Waiter', items: 'Menu', send: 'Send');

  /// The `waiter` role.
  final String waiter;

  /// Menu items, e.g. "Items" or "Services".
  final String items;

  /// Sending an order, e.g. "Send" or "Confirm".
  final String send;

  /// One menu item: an English plural loses its "s" ("Services" -> "Service");
  /// other words, such as "Menu", read "Item".
  String get item => items.length > 1 && items.endsWith('s') && !items.endsWith('ss')
      ? items.substring(0, items.length - 1)
      : 'Item';

  Map<String, dynamic> toJson() => {'waiter': waiter, 'items': items, 'send': send};
}
