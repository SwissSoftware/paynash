import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'customer_api.dart';
import 'customer_models.dart';

/// Create or edit a customer. Before creating, possible duplicates are shown
/// and the user picks the existing customer or creates a new one anyway
/// (spec customer-management, "Possible duplicate warning"). Returns the
/// saved (or picked) customer.
class CustomerFormDialog extends StatefulWidget {
  const CustomerFormDialog({super.key, required this.api, this.customer, this.initialName});

  final CustomerApi api;
  final CustomerData? customer;

  /// Prefills the last name when creating from a search, e.g. in the order screen.
  final String? initialName;

  @override
  State<CustomerFormDialog> createState() => _CustomerFormDialogState();
}

class _CustomerFormDialogState extends State<CustomerFormDialog> {
  static const _fields = [
    ('firstName', 'First name'),
    ('lastName', 'Last name'),
    ('phone', 'Phone'),
    ('email', 'Email'),
    ('street', 'Street'),
    ('postalCode', 'Postal code'),
    ('city', 'City'),
    ('note', 'Note'),
  ];

  late final Map<String, TextEditingController> _text = {
    for (final (name, _) in _fields)
      name: TextEditingController(text: _initial(name)),
  };
  Map<String, String> _errors = const {};
  String? _error;
  bool _busy = false;

  String? _initial(String field) {
    final c = widget.customer;
    if (c == null) return field == 'lastName' ? widget.initialName : null;
    return switch (field) {
      'firstName' => c.firstName,
      'lastName' => c.lastName,
      'phone' => c.phone,
      'email' => c.email,
      'street' => c.street,
      'postalCode' => c.postalCode,
      'city' => c.city,
      'note' => c.note,
      _ => null,
    };
  }

  @override
  void dispose() {
    for (final c in _text.values) {
      c.dispose();
    }
    super.dispose();
  }

  CustomerInput get _input => CustomerInput(
    firstName: _text['firstName']!.text,
    lastName: _text['lastName']!.text,
    phone: _text['phone']!.text,
    email: _text['email']!.text,
    street: _text['street']!.text,
    postalCode: _text['postalCode']!.text,
    city: _text['city']!.text,
    note: _text['note']!.text,
  );

  Future<void> _save() async {
    final input = _input;
    if (input.firstName.trim().isEmpty && input.lastName.trim().isEmpty) {
      setState(() => _errors = {'lastName': 'First name or last name is required'});
      return;
    }
    setState(() {
      _busy = true;
      _errors = const {};
      _error = null;
    });
    try {
      final existing = widget.customer;
      if (existing == null) {
        final matches = await widget.api.duplicates(input);
        if (matches.isNotEmpty && mounted) {
          final choice = await showDialog<CustomerData?>(
            context: context,
            builder: (_) => _DuplicateDialog(matches: matches),
          );
          if (!mounted) return;
          if (choice == null) return; // back to the form
          if (choice.id != _createNew.id) {
            Navigator.pop(context, choice);
            return;
          }
        }
      }
      final saved = existing == null
          ? await widget.api.create(input)
          : await widget.api.update(existing.id, input);
      if (mounted) Navigator.pop(context, saved);
    } on ApiError catch (e) {
      if (mounted) {
        setState(() {
          _errors = e.fieldErrors;
          _error = e.fieldErrors.isEmpty ? e.message : null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final error = Theme.of(context).colorScheme.error;
    return AlertDialog(
      title: Text(widget.customer == null ? 'New customer' : 'Edit ${widget.customer!.name}'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final (name, label) in _fields)
                TextField(
                  key: Key('customer-$name'),
                  controller: _text[name],
                  maxLines: name == 'note' ? 3 : 1,
                  keyboardType: switch (name) {
                    'phone' => TextInputType.phone,
                    'email' => TextInputType.emailAddress,
                    _ => TextInputType.text,
                  },
                  decoration: InputDecoration(labelText: label, errorText: _errors[name]),
                ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_error!, style: TextStyle(color: error)),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          key: const Key('customer-save'),
          onPressed: _busy ? null : _save,
          child: const Text('Save'),
        ),
      ],
    );
  }
}

/// Marker for "create anyway" in the duplicate dialog.
final _createNew = CustomerData(id: -1, number: -1, name: '');

class _DuplicateDialog extends StatelessWidget {
  const _DuplicateDialog({required this.matches});

  final List<CustomerData> matches;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      key: const Key('customer-duplicates'),
      title: const Text('This customer may already exist'),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Tap a customer to use it, or create a new one anyway.'),
            for (final c in matches)
              ListTile(
                key: Key('duplicate-${c.id}'),
                title: Text(c.name),
                subtitle: Text(['No. ${c.number}', ?c.contact].join(' · ')),
                onTap: () => Navigator.pop(context, c),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Back')),
        FilledButton(
          key: const Key('customer-create-anyway'),
          onPressed: () => Navigator.pop(context, _createNew),
          child: const Text('Create new anyway'),
        ),
      ],
    );
  }
}
