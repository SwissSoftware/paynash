import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../auth/session.dart';
import 'staff_api.dart';

/// Admin: list, create, edit/deactivate staff and reset PINs.
class StaffScreen extends StatefulWidget {
  const StaffScreen({super.key, required this.api, required this.me});

  final StaffApi api;
  final Me me;

  @override
  State<StaffScreen> createState() => _StaffScreenState();
}

class _StaffScreenState extends State<StaffScreen> {
  List<StaffMember>? _staff;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final staff = await widget.api.list();
      if (mounted) {
        setState(() {
          _staff = staff;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _openForm([StaffMember? member]) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => _StaffFormDialog(api: widget.api, member: member),
    );
    if (saved == true) await _load();
  }

  Future<void> _resetPin(StaffMember member) async {
    final done = await showDialog<bool>(
      context: context,
      builder: (_) => _ResetPinDialog(api: widget.api, member: member),
    );
    if (done == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('PIN for ${member.displayName} was reset')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final staff = _staff;
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('staff-add'),
        onPressed: () => _openForm(),
        icon: const Icon(Icons.person_add),
        label: const Text('Add staff'),
      ),
      body: _error != null
          ? Center(child: Text(_error!))
          : staff == null
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.only(bottom: 88),
                  children: [
                    for (final m in staff)
                      ListTile(
                        key: Key('staff-${m.username}'),
                        leading: Icon(m.role == Role.admin
                            ? Icons.admin_panel_settings
                            : Icons.person),
                        title: Text(m.displayName,
                            style: m.active
                                ? null
                                : const TextStyle(
                                    decoration: TextDecoration.lineThrough)),
                        subtitle: Text(
                            '${m.username} · ${m.role.name}${m.active ? '' : ' · inactive'}'),
                        onTap: () => _openForm(m),
                        trailing: IconButton(
                          tooltip: 'Reset PIN',
                          icon: const Icon(Icons.password),
                          onPressed: () => _resetPin(m),
                        ),
                      ),
                  ],
                ),
    );
  }
}

// PINs set here belong to someone else, so they are shown in plain text (the
// admin passes them on) and are not offered to password managers.
final _pinFormatters = [
  FilteringTextInputFormatter.digitsOnly,
  LengthLimitingTextInputFormatter(8),
];

class _StaffFormDialog extends StatefulWidget {
  const _StaffFormDialog({required this.api, this.member});

  final StaffApi api;
  final StaffMember? member;

  @override
  State<_StaffFormDialog> createState() => _StaffFormDialogState();
}

class _StaffFormDialogState extends State<_StaffFormDialog> {
  late final _username = TextEditingController(text: widget.member?.username);
  late final _displayName =
      TextEditingController(text: widget.member?.displayName);
  final _pin = TextEditingController();
  late Role _role = widget.member?.role ?? Role.waiter;
  late bool _active = widget.member?.active ?? true;
  Map<String, String> _fieldErrors = {};
  String? _error;
  bool _busy = false;

  bool get _isNew => widget.member == null;

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
      _fieldErrors = {};
    });
    try {
      if (_isNew) {
        await widget.api.create(
            username: _username.text.trim(),
            displayName: _displayName.text.trim(),
            role: _role,
            pin: _pin.text);
      } else {
        await widget.api.update(widget.member!.id,
            displayName: _displayName.text.trim(), role: _role, active: _active);
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
      title: Text(_isNew ? 'Add staff' : 'Edit ${widget.member!.displayName}'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              key: const Key('staff-username'),
              controller: _username,
              enabled: _isNew,
              decoration: InputDecoration(
                  labelText: 'Username', errorText: _fieldErrors['username']),
            ),
            TextField(
              key: const Key('staff-display-name'),
              controller: _displayName,
              decoration: InputDecoration(
                  labelText: 'Display name',
                  errorText: _fieldErrors['displayName']),
            ),
            if (_isNew)
              TextField(
                key: const Key('staff-pin'),
                controller: _pin,
                keyboardType: TextInputType.number,
                autofillHints: const [],
                inputFormatters: _pinFormatters,
                decoration: InputDecoration(
                    labelText: 'PIN (4–8 digits)', errorText: _fieldErrors['pin']),
              ),
            const SizedBox(height: 16),
            SegmentedButton<Role>(
              segments: const [
                ButtonSegment(value: Role.waiter, label: Text('Waiter')),
                ButtonSegment(value: Role.admin, label: Text('Admin')),
              ],
              selected: {_role},
              onSelectionChanged: (s) => setState(() => _role = s.first),
            ),
            if (!_isNew)
              SwitchListTile(
                key: const Key('staff-active'),
                title: const Text('Active'),
                subtitle: const Text('Inactive staff cannot log in'),
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
            key: const Key('staff-save'),
            onPressed: _busy ? null : _save,
            child: const Text('Save')),
      ],
    );
  }
}

class _ResetPinDialog extends StatefulWidget {
  const _ResetPinDialog({required this.api, required this.member});

  final StaffApi api;
  final StaffMember member;

  @override
  State<_ResetPinDialog> createState() => _ResetPinDialogState();
}

class _ResetPinDialogState extends State<_ResetPinDialog> {
  final _pin = TextEditingController();
  String? _error;

  Future<void> _save() async {
    try {
      await widget.api.resetPin(widget.member.id, _pin.text);
      if (mounted) Navigator.pop(context, true);
    } on ApiError catch (e) {
      setState(() => _error = e.fieldErrors['pin'] ?? e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('Reset PIN for ${widget.member.displayName}'),
      content: TextField(
        key: const Key('reset-pin'),
        controller: _pin,
        keyboardType: TextInputType.number,
        autofillHints: const [],
        autofocus: true,
        inputFormatters: _pinFormatters,
        decoration:
            InputDecoration(labelText: 'New PIN (4–8 digits)', errorText: _error),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel')),
        FilledButton(
            key: const Key('reset-pin-save'),
            onPressed: _save,
            child: const Text('Save')),
      ],
    );
  }
}
