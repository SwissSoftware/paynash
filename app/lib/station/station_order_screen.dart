import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import '../orders/order_models.dart';
import 'station_api.dart';

/// An order opened from a station ticket. Lines can be cancelled and the order
/// voided, each confirmed with the username and PIN of the staff member doing
/// it (spec order-taking, "PIN confirmation for station cancellations").
class StationOrderScreen extends StatefulWidget {
  const StationOrderScreen({super.key, required this.api, required this.orderId});

  final StationApi api;
  final int orderId;

  @override
  State<StationOrderScreen> createState() => _StationOrderScreenState();
}

class _StationOrderScreenState extends State<StationOrderScreen> {
  OrderData? _order;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final order = await widget.api.order(widget.orderId);
      if (mounted) setState(() => _order = order);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _confirm(
    String title,
    String action,
    Future<OrderData> Function(String username, String pin, String reason) op,
  ) async {
    final order = await showDialog<OrderData>(
      context: context,
      builder: (_) => ConfirmWithPinDialog(title: title, action: action, onConfirm: op),
    );
    if (order != null && mounted) {
      setState(() => _order = order);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$action: done')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final open = order != null && order.status == OrderStatus.sent;
    return Scaffold(
      appBar: AppBar(
        title: Text(order == null ? 'Order' : 'Table ${order.tableLabel} · #${order.id}'),
        actions: [
          if (open)
            TextButton.icon(
              key: const Key('station-void'),
              onPressed: () => _confirm(
                'Void the order for table ${order.tableLabel}?',
                'Void order',
                (u, p, r) => widget.api.voidOrder(order, username: u, pin: p, reason: r),
              ),
              icon: const Icon(Icons.block),
              label: const Text('Void order'),
            ),
        ],
      ),
      body: order == null
          ? Center(child: _error != null ? Text(_error!) : const CircularProgressIndicator())
          : ListView(
              children: [
                if (order.status == OrderStatus.voided)
                  ListTile(
                    key: const Key('station-order-voided'),
                    title: Text(
                      '${withWhom('Voided', order.voidedBy, order.voidedAtStation)}: '
                      '${order.voidReason}',
                    ),
                  ),
                for (final line in order.lines)
                  ListTile(
                    key: Key('station-line-${line.id}'),
                    leading: ItemTypeBadge(line.type),
                    title: Text(
                      '${line.qty} × ${line.name}',
                      style: line.status == LineStatus.cancelled
                          ? const TextStyle(decoration: TextDecoration.lineThrough)
                          : null,
                    ),
                    subtitle: line.status == LineStatus.cancelled
                        ? Text(
                            '${withWhom('Cancelled', line.cancelledBy, line.cancelledAtStation)}: '
                            '${line.cancelReason}',
                          )
                        : line.note == null
                        ? null
                        : Text(line.note!),
                    trailing: open && line.status == LineStatus.sent
                        ? OutlinedButton.icon(
                            key: Key('station-cancel-${line.id}'),
                            onPressed: () => _confirm(
                              'Cancel ${line.qty} × ${line.name}?',
                              'Cancel line',
                              (u, p, r) => widget.api.cancelLine(
                                order.id,
                                line.id,
                                username: u,
                                pin: p,
                                reason: r,
                              ),
                            ),
                            icon: const Icon(Icons.block),
                            label: const Text('Cancel'),
                          )
                        : null,
                  ),
              ],
            ),
    );
  }
}

/// Asks for the reason, the username, and the PIN, then runs [onConfirm]. A
/// wrong PIN is shown in the dialog; the PIN field is emptied after every
/// attempt so the next person does not see it.
class ConfirmWithPinDialog extends StatefulWidget {
  const ConfirmWithPinDialog({
    super.key,
    required this.title,
    required this.action,
    required this.onConfirm,
  });

  final String title;
  final String action;
  final Future<OrderData> Function(String username, String pin, String reason) onConfirm;

  @override
  State<ConfirmWithPinDialog> createState() => _ConfirmWithPinDialogState();
}

class _ConfirmWithPinDialogState extends State<ConfirmWithPinDialog> {
  final _reason = TextEditingController();
  final _username = TextEditingController();
  final _pin = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _reason.dispose();
    _username.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_reason.text.trim().isEmpty || _username.text.trim().isEmpty || _pin.text.isEmpty) {
      setState(() => _error = 'Reason, username, and PIN are required');
      return;
    }
    final pin = _pin.text;
    setState(() {
      _busy = true;
      _error = null;
    });
    _pin.clear();
    try {
      final order = await widget.onConfirm(_username.text.trim(), pin, _reason.text.trim());
      if (mounted) Navigator.of(context).pop(order);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.fieldErrors['pin'] ?? e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              key: const Key('confirm-reason'),
              controller: _reason,
              maxLength: 140,
              decoration: const InputDecoration(labelText: 'Reason'),
            ),
            TextField(
              key: const Key('confirm-username'),
              controller: _username,
              autocorrect: false,
              decoration: const InputDecoration(labelText: 'Your username'),
            ),
            TextField(
              key: const Key('confirm-pin'),
              controller: _pin,
              obscureText: true,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Your PIN'),
              onSubmitted: (_) => _submit(),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(
                  _error!,
                  key: const Key('confirm-error'),
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: const Text('Back'),
        ),
        FilledButton(
          key: const Key('confirm-submit'),
          onPressed: _busy ? null : _submit,
          child: Text(widget.action),
        ),
      ],
    );
  }
}
