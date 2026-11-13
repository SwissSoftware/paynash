import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'settings_api.dart';
import 'settings_controller.dart';
import 'settings_models.dart';

/// Admin: installation mode, business details, VAT, and bank details
/// (spec venue-mode). Invoices use the details valid when they are issued.
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
  bool _vat = false;
  Map<String, String> _errors = const {};
  String? _modeError;
  bool _saving = false;

  AppSettings get _settings => widget.controller.settings!;

  @override
  void initState() {
    super.initState();
    _fill(_settings);
  }

  @override
  void dispose() {
    for (final c in _text.values) {
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

  Future<void> _changeMode(VenueMode mode) async {
    final label = mode == VenueMode.salon ? 'salon' : 'event / restaurant';
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Switch to $label mode?'),
        content: const Text(
          'This is only possible while the installation has no orders. All devices show the new '
          'functions after their next refresh.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            key: const Key('mode-confirm'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Switch'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      widget.controller.update(await widget.api.changeMode(mode));
      if (mounted) setState(() => _modeError = null);
    } on ApiError catch (e) {
      if (mounted) setState(() => _modeError = e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final mode = _settings.mode;
    final salon = mode == VenueMode.salon;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Mode', style: text.titleMedium),
        const SizedBox(height: 8),
        SegmentedButton<VenueMode>(
          key: const Key('mode-select'),
          segments: const [
            ButtonSegment(value: VenueMode.event, label: Text('Event / restaurant')),
            ButtonSegment(value: VenueMode.salon, label: Text('Salon')),
          ],
          selected: {mode},
          onSelectionChanged: (s) => _changeMode(s.single),
        ),
        if (_modeError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _modeError!,
              key: const Key('mode-error'),
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        const SizedBox(height: 24),
        Text('Business details', style: text.titleMedium),
        Text(
          salon
              ? 'Shown on every invoice. Name, street, postal code, and city are required.'
              : 'Only needed in salon mode.',
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
