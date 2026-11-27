import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../orders/order_history_screen.dart' show formatTime;
import 'printers_api.dart';

/// Admin: the printers, with a test print. Each printer prints the lines of
/// the categories assigned to it and/or invoice receipts; a category may go to
/// several printers, and categories without a printer are not printed
/// (change printer-category-routing).
class PrintersScreen extends StatefulWidget {
  const PrintersScreen({super.key, required this.api});

  final PrintersApi api;

  @override
  State<PrintersScreen> createState() => _PrintersScreenState();
}

class _PrintersScreenState extends State<PrintersScreen> {
  List<PrinterData>? _printers;
  List<PrinterCategory> _unprinted = const [];
  String? _error;
  int? _testing;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final list = await widget.api.list();
      if (mounted) {
        setState(() {
          _printers = list.printers;
          _unprinted = list.unprintedCategories;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _openForm([PrinterData? printer]) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => _PrinterFormDialog(api: widget.api, printer: printer),
    );
    if (saved == true) await _load();
  }

  Future<void> _test(PrinterData printer) async {
    setState(() => _testing = printer.id);
    String message;
    try {
      final error = await widget.api.testPrint(printer.id);
      message = error == null
          ? 'Test print sent to ${printer.name}'
          : 'Test print on ${printer.name} failed: $error';
    } on ApiError catch (e) {
      message = e.message;
    } finally {
      if (mounted) setState(() => _testing = null);
    }
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final printers = _printers;
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('printer-add'),
        onPressed: () => _openForm(),
        icon: const Icon(Icons.add),
        label: const Text('Add printer'),
      ),
      body: _error != null
          ? Center(child: Text(_error!))
          : printers == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.only(bottom: 88),
              children: [
                if (_unprinted.isNotEmpty)
                  ListTile(
                    key: const Key('printer-unprinted'),
                    leading: const Icon(Icons.info_outline),
                    title: Text(
                      'Not printed: ${_unprinted.map((c) => c.name).join(', ')}',
                    ),
                    subtitle: const Text(
                      'No active printer prints these categories. Assign them to a printer '
                      'if their tickets are needed.',
                    ),
                  ),
                for (final p in printers)
                  ListTile(
                    key: Key('printer-${p.name}'),
                    leading: Icon(
                      p.printsReceipts && !p.printsTickets
                          ? Icons.receipt_long
                          : Icons.print_outlined,
                    ),
                    title: Text(
                      p.name,
                      style: p.active
                          ? null
                          : const TextStyle(
                              decoration: TextDecoration.lineThrough,
                            ),
                    ),
                    subtitle: Text(
                      [
                        '${p.address} · ${p.deviceId}${p.active ? '' : ' · inactive'}',
                        _prints(p),
                        if (p.active) 'Status: ${p.status}',
                        for (final s in p.stations)
                          '${s.name}: ${s.lastSeenAt == null ? 'never connected' : 'last seen ${formatTime(s.lastSeenAt!)}'}',
                      ].join('\n'),
                      key: Key('printer-info-${p.name}'),
                      style:
                          p.active &&
                              p.status != 'ready' &&
                              p.status != 'unknown'
                          ? TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            )
                          : null,
                    ),
                    isThreeLine: true,
                    onTap: () => _openForm(p),
                    trailing: TextButton.icon(
                      key: Key('printer-test-${p.name}'),
                      onPressed: _testing == null ? () => _test(p) : null,
                      icon: _testing == p.id
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.print),
                      label: const Text('Test print'),
                    ),
                  ),
              ],
            ),
    );
  }
}

/// "Tickets: Pizza, Dessert · Receipts", or "Prints nothing".
String _prints(PrinterData p) {
  final parts = [
    if (p.printsTickets)
      'Tickets: ${p.categories.isEmpty ? 'no categories' : p.categories.map((c) => c.name).join(', ')}',
    if (p.printsReceipts) 'Receipts',
  ];
  return parts.isEmpty ? 'Prints nothing' : parts.join(' · ');
}

class _PrinterFormDialog extends StatefulWidget {
  const _PrinterFormDialog({required this.api, this.printer});

  final PrintersApi api;
  final PrinterData? printer;

  @override
  State<_PrinterFormDialog> createState() => _PrinterFormDialogState();
}

class _PrinterFormDialogState extends State<_PrinterFormDialog> {
  late final _name = TextEditingController(text: widget.printer?.name);
  late final _address = TextEditingController(text: widget.printer?.address);
  late final _deviceId = TextEditingController(
    text: widget.printer?.deviceId ?? 'local_printer',
  );
  late bool _printsTickets = widget.printer?.printsTickets ?? true;
  late bool _printsReceipts = widget.printer?.printsReceipts ?? false;
  late final Set<int> _categoryIds = {
    for (final c in widget.printer?.categories ?? const []) c.id,
  };
  late bool _active = widget.printer?.active ?? true;
  List<PrinterCategory>? _categories;
  Map<String, String> _fieldErrors = {};
  String? _error;
  bool _busy = false;

  bool get _isNew => widget.printer == null;

  @override
  void initState() {
    super.initState();
    _loadCategories();
  }

  Future<void> _loadCategories() async {
    try {
      final categories = await widget.api.categories();
      if (mounted) setState(() => _categories = categories);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
      _fieldErrors = {};
    });
    try {
      if (_isNew) {
        await widget.api.create(
          name: _name.text.trim(),
          address: _address.text.trim(),
          deviceId: _deviceId.text.trim(),
          printsTickets: _printsTickets,
          printsReceipts: _printsReceipts,
          categoryIds: _categoryIds.toList(),
          active: _active,
        );
      } else {
        await widget.api.update(
          widget.printer!.id,
          name: _name.text.trim(),
          address: _address.text.trim(),
          deviceId: _deviceId.text.trim(),
          printsTickets: _printsTickets,
          printsReceipts: _printsReceipts,
          categoryIds: _categoryIds.toList(),
          active: _active,
        );
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiError catch (e) {
      if (mounted) {
        setState(() {
          _fieldErrors = e.fieldErrors;
          _error = e.fieldErrors.isEmpty ? e.message : null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(_isNew ? 'Add printer' : 'Edit ${widget.printer!.name}'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                key: const Key('printer-name'),
                controller: _name,
                decoration: InputDecoration(
                  labelText: 'Name',
                  hintText: 'e.g. Kitchen',
                  errorText: _fieldErrors['name'],
                ),
              ),
              TextField(
                key: const Key('printer-address'),
                controller: _address,
                decoration: InputDecoration(
                  labelText: 'Address',
                  hintText: 'e.g. 192.168.1.50',
                  errorText: _fieldErrors['address'],
                ),
              ),
              TextField(
                key: const Key('printer-device-id'),
                controller: _deviceId,
                decoration: InputDecoration(
                  labelText: 'ePOS device ID',
                  errorText: _fieldErrors['deviceId'],
                ),
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                key: const Key('printer-tickets'),
                title: const Text('Tickets'),
                subtitle: const Text(
                  'Prints the lines of the selected categories',
                ),
                value: _printsTickets,
                onChanged: (v) => setState(() => _printsTickets = v),
              ),
              if (_printsTickets) _categoryChips(context),
              if (_fieldErrors['categoryIds'] != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    _fieldErrors['categoryIds']!,
                    key: const Key('printer-categories-error'),
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              SwitchListTile(
                key: const Key('printer-receipts'),
                title: const Text('Receipts'),
                subtitle: const Text('Prints invoice receipts'),
                value: _printsReceipts,
                onChanged: (v) => setState(() => _printsReceipts = v),
              ),
              SwitchListTile(
                key: const Key('printer-active'),
                title: const Text('Active'),
                value: _active,
                onChanged: (v) => setState(() => _active = v),
              ),
              if (_error != null)
                Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          key: const Key('printer-save'),
          onPressed: _busy ? null : _save,
          child: const Text('Save'),
        ),
      ],
    );
  }

  Widget _categoryChips(BuildContext context) {
    final categories = _categories;
    if (categories == null) return const LinearProgressIndicator();
    if (categories.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(horizontal: 16),
        child: Text('No categories yet. Create them in the menu first.'),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Wrap(
        key: const Key('printer-categories'),
        spacing: 8,
        runSpacing: 4,
        children: [
          for (final c in categories)
            FilterChip(
              key: Key('printer-category-${c.name}'),
              label: Text(c.name),
              selected: _categoryIds.contains(c.id),
              onSelected: (on) => setState(
                () => on ? _categoryIds.add(c.id) : _categoryIds.remove(c.id),
              ),
            ),
        ],
      ),
    );
  }
}
