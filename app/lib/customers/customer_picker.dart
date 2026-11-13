import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'customer_api.dart';
import 'customer_form.dart';
import 'customer_models.dart';

/// Picks an active customer for an order, or creates one on the spot (spec
/// order-taking, "Confirm an order for a customer").
class CustomerPickerDialog extends StatefulWidget {
  const CustomerPickerDialog({super.key, required this.api});

  final CustomerApi api;

  @override
  State<CustomerPickerDialog> createState() => _CustomerPickerDialogState();
}

class _CustomerPickerDialogState extends State<CustomerPickerDialog> {
  final _query = TextEditingController();
  Timer? _debounce;
  List<CustomerData>? _results;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _query.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final list = await widget.api.search(_query.text);
      if (mounted) {
        setState(() {
          _results = list;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _create() async {
    final created = await showDialog<CustomerData>(
      context: context,
      builder: (_) => CustomerFormDialog(api: widget.api, initialName: _query.text.trim()),
    );
    if (created != null && mounted) Navigator.pop(context, created);
  }

  @override
  Widget build(BuildContext context) {
    final results = _results;
    return AlertDialog(
      title: const Text('Customer'),
      content: SizedBox(
        width: 420,
        height: 420,
        child: Column(
          children: [
            TextField(
              key: const Key('picker-search'),
              controller: _query,
              autofocus: true,
              onChanged: (_) {
                _debounce?.cancel();
                _debounce = Timer(const Duration(milliseconds: 300), _load);
              },
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Name, number, phone, or email',
              ),
            ),
            Expanded(
              child: _error != null
                  ? Center(child: Text(_error!))
                  : results == null
                  ? const Center(child: CircularProgressIndicator())
                  : results.isEmpty
                  ? const Center(child: Text('No customers found.'))
                  : ListView(
                      children: [
                        for (final c in results)
                          ListTile(
                            key: Key('picker-customer-${c.id}'),
                            title: Text(c.name),
                            subtitle: Text(['No. ${c.number}', ?c.contact].join(' · ')),
                            onTap: () => Navigator.pop(context, c),
                          ),
                      ],
                    ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton.icon(
          key: const Key('picker-new-customer'),
          onPressed: _create,
          icon: const Icon(Icons.person_add),
          label: const Text('New customer'),
        ),
      ],
    );
  }
}
