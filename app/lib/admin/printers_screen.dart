import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import '../menu/menu_models.dart';
import 'printers_api.dart';

/// Admin: the kitchen and bar printers, with a test print. Each type (food,
/// drink) is printed by at most one active printer.
class PrintersScreen extends StatefulWidget {
  const PrintersScreen({super.key, required this.api});

  final PrintersApi api;

  @override
  State<PrintersScreen> createState() => _PrintersScreenState();
}

class _PrintersScreenState extends State<PrintersScreen> {
  List<PrinterData>? _printers;
  String? _error;
  int? _testing;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final printers = await widget.api.list();
      if (mounted) {
        setState(() {
          _printers = printers;
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
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final printers = _printers;
    final missing = printers == null
        ? const <ItemType>[]
        : ItemType.values.where((t) => !printers.any((p) => p.active && p.type == t)).toList();
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
                    for (final type in missing)
                      Material(
                        key: Key('printer-missing-${type.name}'),
                        color: Theme.of(context).colorScheme.errorContainer,
                        child: ListTile(
                          leading: const Icon(Icons.warning_amber),
                          title: Text(
                              'No active printer for ${type == ItemType.food ? 'food' : 'drinks'}. '
                              'Those tickets fail with "no printer configured".'),
                        ),
                      ),
                    for (final p in printers)
                      ListTile(
                        key: Key('printer-${p.name}'),
                        leading: ItemTypeBadge(p.type),
                        title: Text(p.name,
                            style: p.active
                                ? null
                                : const TextStyle(decoration: TextDecoration.lineThrough)),
                        subtitle: Text(
                            '${p.address} · ${p.deviceId}${p.active ? '' : ' · inactive'}'),
                        onTap: () => _openForm(p),
                        trailing: TextButton.icon(
                          key: Key('printer-test-${p.name}'),
                          onPressed: _testing == null ? () => _test(p) : null,
                          icon: _testing == p.id
                              ? const SizedBox.square(
                                  dimension: 16,
                                  child: CircularProgressIndicator(strokeWidth: 2))
                              : const Icon(Icons.print),
                          label: const Text('Test print'),
                        ),
                      ),
                  ],
                ),
    );
  }
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
  late final _deviceId =
      TextEditingController(text: widget.printer?.deviceId ?? 'local_printer');
  late ItemType _type = widget.printer?.type ?? ItemType.food;
  late bool _active = widget.printer?.active ?? true;
  Map<String, String> _fieldErrors = {};
  String? _error;
  bool _busy = false;

  bool get _isNew => widget.printer == null;

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
            type: _type,
            active: _active);
      } else {
        await widget.api.update(widget.printer!.id,
            name: _name.text.trim(),
            address: _address.text.trim(),
            deviceId: _deviceId.text.trim(),
            type: _type,
            active: _active);
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
        width: 380,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              key: const Key('printer-name'),
              controller: _name,
              decoration: InputDecoration(
                  labelText: 'Name', hintText: 'e.g. Kitchen', errorText: _fieldErrors['name']),
            ),
            TextField(
              key: const Key('printer-address'),
              controller: _address,
              decoration: InputDecoration(
                  labelText: 'Address',
                  hintText: 'e.g. 192.168.1.50',
                  errorText: _fieldErrors['address']),
            ),
            TextField(
              key: const Key('printer-device-id'),
              controller: _deviceId,
              decoration: InputDecoration(
                  labelText: 'ePOS device ID', errorText: _fieldErrors['deviceId']),
            ),
            const SizedBox(height: 16),
            SegmentedButton<ItemType>(
              key: const Key('printer-type'),
              segments: const [
                ButtonSegment(value: ItemType.food, label: Text('Food (kitchen)')),
                ButtonSegment(value: ItemType.drink, label: Text('Drinks (bar)')),
              ],
              selected: {_type},
              onSelectionChanged: (s) => setState(() => _type = s.first),
            ),
            if (_fieldErrors['type'] != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(_fieldErrors['type']!,
                    key: const Key('printer-type-error'),
                    style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            SwitchListTile(
              key: const Key('printer-active'),
              title: const Text('Active'),
              value: _active,
              onChanged: (v) => setState(() => _active = v),
            ),
            if (_error != null)
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(
            key: const Key('printer-save'),
            onPressed: _busy ? null : _save,
            child: const Text('Save')),
      ],
    );
  }
}
