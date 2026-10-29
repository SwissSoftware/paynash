import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import 'tables_api.dart';

/// Admin: the restaurant's tables, which waiters pick when taking an order.
/// Sent orders keep their table number as text, so editing or deleting a
/// table never changes them.
class TablesScreen extends StatefulWidget {
  const TablesScreen({super.key, required this.api});

  final TablesApi api;

  @override
  State<TablesScreen> createState() => _TablesScreenState();
}

class _TablesScreenState extends State<TablesScreen> {
  List<TableData>? _tables;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final tables = await widget.api.list();
      if (mounted) {
        setState(() {
          _tables = tables;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _openForm([TableData? table]) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => _TableFormDialog(api: widget.api, table: table),
    );
    if (saved == true) await _load();
  }

  Future<void> _delete(TableData table) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete table ${table.number}?'),
        content: const Text('Orders already sent keep their table number.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
              key: const Key('table-delete-confirm'),
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.delete(table.id);
    } on ApiError catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final tables = _tables;
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('table-add'),
        onPressed: () => _openForm(),
        icon: const Icon(Icons.add),
        label: const Text('Add table'),
      ),
      body: _error != null
          ? Center(child: Text(_error!))
          : tables == null
              ? const Center(child: CircularProgressIndicator())
              : tables.isEmpty
                  ? const Center(
                      child: Text(
                          'No tables yet. Waiters type the table by hand until you add some.'))
                  : ListView(
                      padding: const EdgeInsets.only(bottom: 88),
                      children: [
                        for (final t in tables)
                          ListTile(
                            key: Key('table-${t.number}'),
                            leading: CircleAvatar(child: Text(t.number, maxLines: 1)),
                            title: Text(t.name ?? 'Table ${t.number}',
                                style: t.active
                                    ? null
                                    : const TextStyle(
                                        decoration: TextDecoration.lineThrough)),
                            subtitle: t.active ? null : const Text('inactive'),
                            onTap: () => _openForm(t),
                            trailing: IconButton(
                              key: Key('table-delete-${t.number}'),
                              tooltip: 'Delete',
                              icon: const Icon(Icons.delete_outline),
                              onPressed: () => _delete(t),
                            ),
                          ),
                      ],
                    ),
    );
  }
}

class _TableFormDialog extends StatefulWidget {
  const _TableFormDialog({required this.api, this.table});

  final TablesApi api;
  final TableData? table;

  @override
  State<_TableFormDialog> createState() => _TableFormDialogState();
}

class _TableFormDialogState extends State<_TableFormDialog> {
  late final _number = TextEditingController(text: widget.table?.number);
  late final _name = TextEditingController(text: widget.table?.name);
  late bool _active = widget.table?.active ?? true;
  Map<String, String> _fieldErrors = {};
  String? _error;
  bool _busy = false;

  bool get _isNew => widget.table == null;

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
      _fieldErrors = {};
    });
    final number = _number.text.trim();
    final name = _name.text.trim().isEmpty ? null : _name.text.trim();
    try {
      if (_isNew) {
        await widget.api.create(number: number, name: name);
      } else {
        await widget.api
            .update(widget.table!.id, number: number, name: name, active: _active);
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
      title: Text(_isNew ? 'Add table' : 'Edit table ${widget.table!.number}'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              key: const Key('table-number'),
              controller: _number,
              autofocus: true,
              textCapitalization: TextCapitalization.characters,
              inputFormatters: [LengthLimitingTextInputFormatter(10)],
              decoration: InputDecoration(
                  labelText: 'Number',
                  helperText: 'Shown on orders and tickets',
                  errorText: _fieldErrors['number']),
            ),
            TextField(
              key: const Key('table-name'),
              controller: _name,
              inputFormatters: [LengthLimitingTextInputFormatter(60)],
              decoration: InputDecoration(
                  labelText: 'Name (optional)',
                  hintText: 'e.g. Terrace window',
                  errorText: _fieldErrors['name']),
            ),
            if (!_isNew)
              SwitchListTile(
                key: const Key('table-active'),
                title: const Text('Active'),
                subtitle: const Text('Inactive tables are hidden from waiters'),
                value: _active,
                onChanged: (v) => setState(() => _active = v),
              ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel')),
        FilledButton(
            key: const Key('table-save'),
            onPressed: _busy ? null : _save,
            child: const Text('Save')),
      ],
    );
  }
}
