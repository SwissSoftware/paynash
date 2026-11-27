import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'settings_api.dart';
import 'settings_controller.dart';
import 'settings_models.dart';

/// Admin: business template, features, and terms (spec business-features), and
/// business details, VAT, and bank details (spec venue-mode). Invoices use the
/// details valid when they are issued.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.api, required this.controller});

  final SettingsApi api;
  final SettingsController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const _fields = [
    ('businessName', 'Business name'),
    ('street', 'Street'),
    ('postalCode', 'Postal code'),
    ('city', 'City'),
    ('phone', 'Phone'),
    ('email', 'Email'),
    ('website', 'Website'),
    ('iban', 'IBAN'),
    ('accountHolder', 'Account holder'),
    ('vatUid', 'VAT number (UID)'),
  ];

  late final Map<String, TextEditingController> _text = {
    for (final (name, _) in _fields) name: TextEditingController(),
  };
  late final _waiterTerm = TextEditingController(text: _settings.terms.waiter);
  late final _itemsTerm = TextEditingController(text: _settings.terms.items);
  late final _sendTerm = TextEditingController(text: _settings.terms.send);
  bool _vat = false;
  Map<String, String> _errors = const {};
  Map<String, String> _termErrors = const {};
  String? _featureError;
  bool _saving = false;
  bool _busy = false;

  AppSettings get _settings => widget.controller.settings!;

  @override
  void initState() {
    super.initState();
    _fill(_settings);
  }

  @override
  void dispose() {
    for (final c in [..._text.values, _waiterTerm, _itemsTerm, _sendTerm]) {
      c.dispose();
    }
    super.dispose();
  }

  void _fill(AppSettings s) {
    final values = {
      'businessName': s.businessName,
      'street': s.street,
      'postalCode': s.postalCode,
      'city': s.city,
      'phone': s.phone,
      'email': s.email,
      'website': s.website,
      'iban': s.iban,
      'accountHolder': s.accountHolder,
      'vatUid': s.vatUid,
    };
    for (final e in values.entries) {
      _text[e.key]!.text = e.value ?? '';
    }
    _vat = s.vatEnabled;
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _errors = const {};
    });
    try {
      final saved = await widget.api.save({
        for (final e in _text.entries) e.key: e.value.text.trim(),
        'vatEnabled': _vat,
      });
      widget.controller.update(saved);
      if (!mounted) return;
      setState(() => _fill(saved));
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Settings saved')));
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() => _errors = e.fieldErrors.isEmpty ? {'businessName': e.message} : e.fieldErrors);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<bool> _confirm(String title, String message, String action) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(message),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
            FilledButton(
              key: const Key('settings-confirm'),
              onPressed: () => Navigator.pop(context, true),
              child: Text(action),
            ),
          ],
        ),
      ) ==
      true;

  /// Runs a settings change and shows the new state on every screen.
  Future<void> _apply(Future<AppSettings> Function() change) async {
    setState(() {
      _busy = true;
      _featureError = null;
    });
    try {
      final saved = await change();
      widget.controller.update(saved);
      if (!mounted) return;
      setState(() {
        _waiterTerm.text = saved.terms.waiter;
        _itemsTerm.text = saved.terms.items;
        _sendTerm.text = saved.terms.send;
      });
    } on ApiError catch (e) {
      if (mounted) setState(() => _featureError = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _applyTemplate(BusinessTemplate template) async {
    final ok = await _confirm(
      'Apply the template ${template.label}?',
      'Sets the features and terms of this template. Data such as orders, invoices, and customers '
          'stays unchanged. All devices show the new functions after their next refresh.',
      'Apply',
    );
    if (ok) await _apply(() => widget.api.applyTemplate(template));
  }

  /// Switching off asks first and explains that the data is kept (spec
  /// business-features, "Switching features with existing data").
  Future<void> _toggle(Feature feature, bool on) async {
    if (!on) {
      final ok = await _confirm(
        'Switch off ${feature.label.toLowerCase()}?',
        'Its functions are hidden on all devices, but all data is kept. You can switch it on '
            'again at any time.',
        'Switch off',
      );
      if (!ok) return;
    }
    final features = {..._settings.features};
    on ? features.add(feature) : features.remove(feature);
    await _apply(() => widget.api.saveFeatures(features));
  }

  Future<void> _saveTerms() async {
    setState(() => _termErrors = const {});
    try {
      final saved = await widget.api.saveTerms(
        Terms(
          waiter: _waiterTerm.text.trim(),
          items: _itemsTerm.text.trim(),
          send: _sendTerm.text.trim(),
        ),
      );
      widget.controller.update(saved);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Terms saved')));
    } on ApiError catch (e) {
      if (mounted) setState(() => _termErrors = e.fieldErrors);
    }
  }

  /// Why a switch cannot be used right now, or null.
  String? _blocked(Feature feature) {
    final needed = feature.requires;
    if (!_settings.has(feature) && needed != null && !_settings.has(needed)) {
      return 'Needs ${needed.label.toLowerCase()}';
    }
    final dependent = Feature.values.where((f) => f.requires == feature && _settings.has(f));
    if (_settings.has(feature) && dependent.isNotEmpty) {
      return 'Switch off ${dependent.first.label.toLowerCase()} first';
    }
    return null;
  }

  static const _featureHelp = {
    Feature.tables: 'Orders belong to a table; table administration',
    Feature.stations: 'Station accounts with their ticket view',
    Feature.customers: 'Customer records; orders can have a customer',
    Feature.invoices: 'Invoices, receipts, and PDFs, with or without customer',
    Feature.salesReport: 'Monthly sales report and Excel export',
  };

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final invoices = _settings.has(Feature.invoices);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Business template', style: text.titleMedium),
        Text(
          'Current: ${_settings.template.label}. A template sets features and terms; you can '
          'adjust both afterwards.',
          key: const Key('settings-template-current'),
          style: text.bodySmall,
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final template in BusinessTemplate.values)
              if (template != BusinessTemplate.custom)
                OutlinedButton(
                  key: Key('template-${template.name}'),
                  onPressed: _busy ? null : () => _applyTemplate(template),
                  child: Text(template.label),
                ),
          ],
        ),
        const SizedBox(height: 24),
        Text('Features', style: text.titleMedium),
        for (final feature in Feature.values)
          SwitchListTile(
            key: Key('feature-${feature.key}'),
            contentPadding: EdgeInsets.zero,
            title: Text(feature.label),
            subtitle: Text(_blocked(feature) ?? _featureHelp[feature]!),
            value: _settings.has(feature),
            onChanged: _busy || _blocked(feature) != null ? null : (on) => _toggle(feature, on),
          ),
        if (_featureError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _featureError!,
              key: const Key('feature-error'),
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        const SizedBox(height: 24),
        Text('Terms', style: text.titleMedium),
        Text('How the apps name these things. Stored roles do not change.', style: text.bodySmall),
        const SizedBox(height: 8),
        for (final (key, label, controller) in [
          ('waiter', 'Waiter role', _waiterTerm),
          ('items', 'Menu items', _itemsTerm),
          ('send', 'Sending an order', _sendTerm),
        ])
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: TextField(
              key: Key('term-$key'),
              controller: controller,
              maxLength: 30,
              decoration: InputDecoration(
                labelText: label,
                errorText: _termErrors[key],
                border: const OutlineInputBorder(),
              ),
            ),
          ),
        Align(
          alignment: Alignment.centerRight,
          child: OutlinedButton(
            key: const Key('terms-save'),
            onPressed: _saveTerms,
            child: const Text('Save terms'),
          ),
        ),
        const SizedBox(height: 24),
        Text('Business details', style: text.titleMedium),
        Text(
          invoices
              ? 'Shown on every invoice. Name, street, postal code, and city are required.'
              : 'Only needed while invoices are on.',
          style: text.bodySmall,
        ),
        const SizedBox(height: 8),
        for (final (name, label) in _fields)
          if (name != 'vatUid')
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: TextField(
                key: Key('settings-$name'),
                controller: _text[name],
                decoration: InputDecoration(
                  labelText: label,
                  errorText: _errors[name],
                  border: const OutlineInputBorder(),
                ),
              ),
            ),
        SwitchListTile(
          key: const Key('settings-vat'),
          contentPadding: EdgeInsets.zero,
          title: const Text('Show VAT on invoices'),
          subtitle: const Text('Applies to invoices issued from now on.'),
          value: _vat,
          onChanged: (v) => setState(() => _vat = v),
        ),
        if (_vat)
          TextField(
            key: const Key('settings-vatUid'),
            controller: _text['vatUid'],
            decoration: InputDecoration(
              labelText: 'VAT number (UID)',
              hintText: 'CHE-123.456.789 MWST',
              errorText: _errors['vatUid'],
              border: const OutlineInputBorder(),
            ),
          ),
        const SizedBox(height: 16),
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton(
            key: const Key('settings-save'),
            onPressed: _saving ? null : _save,
            child: const Text('Save'),
          ),
        ),
      ],
    );
  }
}
