import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'order_api.dart';
import 'order_detail_screen.dart';
import 'order_models.dart';
import 'polling.dart';

/// The orders sent on one day, newest first. Refreshes while visible, so
/// orders sent on other devices appear within one poll.
class OrderHistoryScreen extends StatefulWidget {
  const OrderHistoryScreen({super.key, required this.orderApi, required this.isAdmin});

  final OrderApi orderApi;

  /// Admins can cancel lines and void orders in the detail view.
  final bool isAdmin;

  @override
  State<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends State<OrderHistoryScreen> with Polling {
  /// Null means today, as the server sees it.
  DateTime? _day;
  List<OrderSummary>? _orders;
  String? _error;
  bool _offline = false;

  @override
  void initState() {
    super.initState();
    poll();
  }

  @override
  Future<void> poll() async {
    final day = _day;
    try {
      final orders = await widget.orderApi.history(day);
      if (!mounted || day != _day) return;
      setState(() {
        _orders = orders;
        _error = null;
        _offline = false;
      });
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _offline = e.isConnectionError;
        if (!e.isConnectionError || _orders == null) _error = e.message;
      });
    }
  }

  DateTime get _shownDay {
    final now = DateTime.now();
    return _day ?? DateTime(now.year, now.month, now.day);
  }

  void _setDay(DateTime day) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    setState(() {
      _day = day == today ? null : day;
      _orders = null;
    });
    poll();
  }

  Future<void> _pickDay() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _shownDay,
      firstDate: DateTime(2020),
      lastDate: DateTime.now(),
    );
    if (picked != null) _setDay(picked);
  }

  Future<void> _show(int orderId) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            OrderDetailScreen(orderId: orderId, orderApi: widget.orderApi, isAdmin: widget.isAdmin),
      ),
    );
    await poll();
  }

  @override
  Widget build(BuildContext context) {
    final orders = _orders;
    final day = _shownDay;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_offline) const NoConnectionBanner(),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Row(
            children: [
              IconButton(
                key: const Key('history-prev-day'),
                tooltip: 'Previous day',
                icon: const Icon(Icons.chevron_left),
                onPressed: () => _setDay(day.subtract(const Duration(days: 1))),
              ),
              Expanded(
                child: TextButton.icon(
                  key: const Key('history-day'),
                  onPressed: _pickDay,
                  icon: const Icon(Icons.calendar_today),
                  label: Text(_day == null ? 'Today' : formatDate(day)),
                ),
              ),
              IconButton(
                key: const Key('history-next-day'),
                tooltip: 'Next day',
                icon: const Icon(Icons.chevron_right),
                onPressed: _day == null ? null : () => _setDay(day.add(const Duration(days: 1))),
              ),
            ],
          ),
        ),
        const Divider(height: 1),
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
              ? const Center(child: Text('No orders sent on this day.'))
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

  final OrderSummary order;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final voided = order.status == OrderStatus.voided;
    return ListTile(
      key: Key('history-order-${order.id}'),
      onTap: onTap,
      leading: CircleAvatar(child: Text(order.tableLabel, style: text.labelLarge, maxLines: 1)),
      title: Text('Table ${order.tableLabel}'),
      subtitle: Text('${formatTime(order.sentAt)} · ${order.waiter}${voided ? ' · Voided' : ''}'),
      trailing: Text(
        'CHF ${order.total}',
        style: text.titleMedium?.copyWith(decoration: voided ? TextDecoration.lineThrough : null),
      ),
    );
  }
}

/// 29.10.2026
String formatDate(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.${d.year}';

/// 19:30, in the device's time zone.
String formatTime(DateTime t) {
  final local = t.toLocal();
  return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
}
