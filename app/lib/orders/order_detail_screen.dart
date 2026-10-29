import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import 'order_api.dart';
import 'order_history_screen.dart';
import 'order_models.dart';
import 'polling.dart';
import 'text_dialog.dart';

/// One sent order with its lines. Sent orders are final; admins can still
/// cancel single lines or void the whole order, which prints cancellation
/// tickets.
class OrderDetailScreen extends StatefulWidget {
  const OrderDetailScreen({
    super.key,
    required this.orderId,
    required this.orderApi,
    required this.isAdmin,
  });

  final int orderId;
  final OrderApi orderApi;
  final bool isAdmin;

  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen> with Polling {
  OrderData? _order;
  String? _error;
  bool _offline = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    poll();
  }

  @override
  Future<void> poll() async {
    if (_busy) return;
    try {
      final order = await widget.orderApi.load(widget.orderId);
      if (!mounted || _busy) return;
      setState(() {
        _order = order;
        _offline = false;
        _error = null;
      });
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _offline = e.isConnectionError;
        if (!e.isConnectionError || _order == null) _error = e.message;
      });
    }
  }

  Future<String?> _askReason(String title, String action) => showDialog<String>(
    context: context,
    builder: (_) => TextDialog(title: title, label: 'Reason', action: action, required: true),
  );

  Future<void> _run(Future<OrderData> Function() op) async {
    setState(() => _busy = true);
    try {
      final order = await op();
      if (mounted) setState(() => _order = order);
    } on ApiError catch (e) {
      if (!mounted) return;
      if (e.isConnectionError) setState(() => _offline = true);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    // Shows changes made elsewhere, e.g. after a 409.
    await poll();
  }

  Future<void> _cancel(OrderLineData line) async {
    final reason = await _askReason('Cancel ${line.qty} × ${line.name}?', 'Cancel line');
    if (reason == null) return;
    await _run(() => widget.orderApi.cancelLine(widget.orderId, line.id, reason));
  }

  Future<void> _void(OrderData order) async {
    final reason = await _askReason('Void the order for table ${order.tableLabel}?', 'Void order');
    if (reason == null) return;
    await _run(() => widget.orderApi.voidOrder(order, reason));
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final text = Theme.of(context).textTheme;
    final canChange =
        widget.isAdmin && !_busy && !_offline && order != null && order.status == OrderStatus.sent;

    Widget body;
    if (_error != null && order == null) {
      body = Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            TextButton(onPressed: poll, child: const Text('Retry')),
          ],
        ),
      );
    } else if (order == null) {
      body = const Center(child: CircularProgressIndicator());
    } else {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Text(
              'Sent ${formatDate(order.sentAt.toLocal())} ${formatTime(order.sentAt)} by ${order.waiter}',
              key: const Key('order-sent-info'),
            ),
          ),
          if (order.status == OrderStatus.voided)
            Material(
              key: const Key('order-voided'),
              color: Theme.of(context).colorScheme.errorContainer,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: Text('Voided: ${order.voidReason}'),
              ),
            ),
          Expanded(
            child: ListView(
              children: [
                for (final line in order.lines)
                  _LineTile(line: line, onCancel: canChange ? () => _cancel(line) : null),
              ],
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Row(
              children: [
                Text('Total', style: text.titleMedium),
                const Spacer(),
                Text('CHF ${order.total}', key: const Key('order-total'), style: text.titleLarge),
              ],
            ),
          ),
        ],
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(order == null ? 'Order' : 'Table ${order.tableLabel} · #${order.id}'),
        actions: [
          if (widget.isAdmin && order != null && order.status == OrderStatus.sent)
            TextButton.icon(
              key: const Key('order-void'),
              onPressed: canChange ? () => _void(order) : null,
              icon: const Icon(Icons.block),
              label: const Text('Void order'),
            ),
        ],
      ),
      body: Column(
        children: [
          if (_offline) const NoConnectionBanner(),
          Expanded(child: body),
        ],
      ),
    );
  }
}

class _LineTile extends StatelessWidget {
  const _LineTile({required this.line, required this.onCancel});

  final OrderLineData line;

  /// Null when the line cannot be cancelled (waiter, or already cancelled).
  final VoidCallback? onCancel;

  @override
  Widget build(BuildContext context) {
    final cancelled = line.status == LineStatus.cancelled;
    final subtitle = [
      if (line.note != null) line.note!,
      if (cancelled) 'Cancelled: ${line.cancelReason}',
    ].join('\n');

    return ListTile(
      key: Key('line-${line.id}'),
      leading: ItemTypeBadge(line.type),
      title: Text(
        '${line.qty} × ${line.name}',
        style: cancelled
            ? const TextStyle(decoration: TextDecoration.lineThrough, color: Colors.grey)
            : null,
      ),
      subtitle: subtitle.isEmpty ? null : Text(subtitle),
      isThreeLine: subtitle.contains('\n'),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(line.lineTotal),
          if (!cancelled && onCancel != null)
            IconButton(
              key: Key('line-cancel-${line.id}'),
              tooltip: 'Cancel line',
              icon: const Icon(Icons.block),
              onPressed: onCancel,
            ),
        ],
      ),
    );
  }
}
