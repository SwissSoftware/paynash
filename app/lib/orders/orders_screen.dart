import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../menu/menu_api.dart';
import 'order_api.dart';
import 'order_models.dart';
import 'order_screen.dart';
import 'polling.dart';

/// All open orders, plus a field to open (or jump to) a table's order.
class OrdersScreen extends StatefulWidget {
  const OrdersScreen({super.key, required this.orderApi, required this.menuApi});

  final OrderApi orderApi;
  final MenuApi menuApi;

  @override
  State<OrdersScreen> createState() => _OrdersScreenState();
}

class _OrdersScreenState extends State<OrdersScreen> with Polling {
  final _table = TextEditingController();
  List<OpenOrderSummary>? _orders;
  String? _error;
  String? _tableError;
  bool _offline = false;
  bool _opening = false;

  @override
  void initState() {
    super.initState();
    poll();
  }

  @override
  void dispose() {
    _table.dispose();
    super.dispose();
  }

  @override
  Future<void> poll() async {
    try {
      final orders = await widget.orderApi.openOrders();
      if (mounted) {
        setState(() {
          _orders = orders;
          _error = null;
          _offline = false;
        });
      }
    } on ApiError catch (e) {
      if (mounted) {
        setState(() {
          _offline = e.isConnectionError;
          if (!e.isConnectionError || _orders == null) _error = e.message;
        });
      }
    }
  }

  Future<void> _openTable() async {
    final label = _table.text.trim();
    if (!RegExp(r'^[A-Za-z0-9]{1,10}$').hasMatch(label)) {
      setState(() => _tableError = '1 to 10 letters or digits');
      return;
    }
    setState(() {
      _tableError = null;
      _opening = true;
    });
    try {
      final order = await widget.orderApi.open(label);
      _table.clear();
      await _show(order.id);
    } on ApiError catch (e) {
      if (mounted) setState(() => _tableError = e.fieldErrors['tableLabel'] ?? e.message);
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  Future<void> _show(int orderId) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            OrderScreen(orderId: orderId, orderApi: widget.orderApi, menuApi: widget.menuApi),
      ),
    );
    await poll();
  }

  @override
  Widget build(BuildContext context) {
    final orders = _orders;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_offline) const NoConnectionBanner(),
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextField(
                  key: const Key('table-input'),
                  controller: _table,
                  decoration: InputDecoration(
                    labelText: 'Table',
                    errorText: _tableError,
                    border: const OutlineInputBorder(),
                  ),
                  textCapitalization: TextCapitalization.characters,
                  inputFormatters: [LengthLimitingTextInputFormatter(10)],
                  onSubmitted: (_) => _openTable(),
                ),
              ),
              const SizedBox(width: 12),
              SizedBox(
                height: 56,
                child: FilledButton.icon(
                  key: const Key('table-open'),
                  onPressed: _opening ? null : _openTable,
                  icon: const Icon(Icons.table_restaurant),
                  label: const Text('Open'),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: _error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(_error!),
                      TextButton(onPressed: poll, child: const Text('Retry')),
                    ],
                  ),
                )
              : orders == null
              ? const Center(child: CircularProgressIndicator())
              : orders.isEmpty
              ? const Center(child: Text('No open orders.'))
              : ListView.separated(
                  itemCount: orders.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (context, i) =>
                      _OrderTile(order: orders[i], onTap: () => _show(orders[i].id)),
                ),
        ),
      ],
    );
  }
}

class _OrderTile extends StatelessWidget {
  const _OrderTile({required this.order, required this.onTap});

  final OpenOrderSummary order;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ListTile(
      key: Key('open-order-${order.id}'),
      onTap: onTap,
      leading: CircleAvatar(child: Text(order.tableLabel, style: text.labelLarge, maxLines: 1)),
      title: Text('Table ${order.tableLabel}'),
      subtitle: Text(
        '${order.waiter} · ${order.ageMinutes} min'
        '${order.unsentLines > 0 ? ' · ${order.unsentLines} not sent' : ''}',
      ),
      trailing: Text('CHF ${order.total}', style: text.titleMedium),
    );
  }
}
