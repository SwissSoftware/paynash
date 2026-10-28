import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import '../menu/menu_api.dart';
import '../menu/menu_browser.dart';
import '../menu/menu_models.dart';
import 'order_api.dart';
import 'order_models.dart';
import 'polling.dart';

enum _SendState { idle, sending, sent }

/// Taking an order for one table: tap menu tiles to add lines, adjust
/// quantity and notes, send the unsent lines as a round, and cancel sent lines.
class OrderScreen extends StatefulWidget {
  const OrderScreen({
    super.key,
    required this.orderId,
    required this.orderApi,
    required this.menuApi,
  });

  final int orderId;
  final OrderApi orderApi;
  final MenuApi menuApi;

  @override
  State<OrderScreen> createState() => _OrderScreenState();
}

class _OrderScreenState extends State<OrderScreen> with Polling {
  OrderData? _order;
  List<MenuCategoryData>? _menu;
  String? _error;
  bool _offline = false;
  _SendState _sendState = _SendState.idle;
  int? _sentRound;

  /// Changes run one after another, each based on the result of the previous
  /// one, so quick taps do not race each other.
  Future<void> _queue = Future.value();

  /// Incremented whenever a change is applied; a poll that started earlier
  /// is discarded so it cannot show an older state.
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    poll();
    _loadMenu();
  }

  @override
  Future<void> poll() async {
    final generation = _generation;
    try {
      final order = await widget.orderApi.load(widget.orderId);
      if (!mounted || generation != _generation) return;
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

  Future<void> _loadMenu() async {
    try {
      final menu = await widget.menuApi.load();
      if (mounted) setState(() => _menu = menu);
    } on ApiError catch (e) {
      if (mounted && _menu == null) setState(() => _error = e.message);
    }
  }

  void _apply(OrderData order) {
    _generation++;
    setState(() {
      _order = order;
      _offline = false;
      if (order.hasUnsentLines) {
        _sendState = _SendState.idle;
        _sentRound = null;
      }
    });
  }

  /// Runs [step] after the changes queued before it.
  Future<void> _enqueue(Future<void> Function() step) =>
      // An unexpected error must not block the changes queued after it.
      _queue = _queue.then((_) => step()).catchError((Object _) {});

  /// Runs a change based on the latest order and reports failures.
  Future<void> _change(Future<OrderData> Function(OrderData current) op) => _enqueue(() async {
    final current = _order;
    if (current == null || !mounted) return;
    try {
      _apply(await op(current));
    } on ApiError catch (e) {
      await _handleError(e);
    }
  });

  Future<void> _handleError(ApiError e) async {
    if (!mounted) return;
    if (e.isConnectionError) {
      setState(() => _offline = true);
    } else if (e.details['menuItemInactive'] == true) {
      await _loadMenu();
    } else if (e.status == 409 || e.status == 404) {
      // Changed on another device meanwhile: show the current state.
      await poll();
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  void _addItem(MenuItemData item) {
    _change((order) {
      // Tapping a tile again raises the quantity of its unsent line, unless
      // that line has a note (then it is a different request from the guest).
      final existing = order.lines.where(
        (l) =>
            l.status == LineStatus.unsent &&
            l.menuItemId == item.id &&
            l.note == null &&
            l.qty < 99,
      );
      return existing.isEmpty
          ? widget.orderApi.addLine(order.id, item.id)
          : widget.orderApi.updateLine(order.id, existing.first, qty: existing.first.qty + 1);
    });
  }

  void _setQty(OrderLineData line, int qty) {
    _change((order) => widget.orderApi.updateLine(order.id, line, qty: qty, note: line.note));
  }

  void _remove(OrderLineData line) {
    _change((order) => widget.orderApi.removeLine(order.id, line.id));
  }

  Future<void> _editNote(OrderLineData line) async {
    final note = await showDialog<String>(
      context: context,
      builder: (_) => _TextDialog(
        title: 'Note for ${line.name}',
        label: 'Note',
        initial: line.note,
        action: 'Save',
      ),
    );
    if (note == null) return;
    _change((order) {
      final current = order.lines.firstWhere((l) => l.id == line.id, orElse: () => line);
      return widget.orderApi.updateLine(order.id, current, qty: current.qty, note: note);
    });
  }

  Future<void> _cancel(OrderLineData line) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (_) => _TextDialog(
        title: 'Cancel ${line.qty} × ${line.name}?',
        label: 'Reason',
        action: 'Cancel line',
        required: true,
      ),
    );
    if (reason == null) return;
    _change((order) => widget.orderApi.cancelLine(order.id, line.id, reason));
  }

  Future<void> _send() async {
    setState(() => _sendState = _SendState.sending);
    await _enqueue(() async {
      try {
        final result = await widget.orderApi.send(widget.orderId);
        _apply(result.order);
        if (mounted) {
          setState(() {
            _sendState = _SendState.sent;
            _sentRound = result.round;
          });
        }
      } on ApiError catch (e) {
        if (mounted) setState(() => _sendState = _SendState.idle);
        await _handleError(e);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final order = _order;
    final menu = _menu;
    final wide = MediaQuery.sizeOf(context).width >= 720;

    Widget body;
    if (_error != null && (order == null || menu == null)) {
      body = Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            TextButton(
              onPressed: () {
                setState(() => _error = null);
                poll();
                _loadMenu();
              },
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    } else if (order == null || menu == null) {
      body = const Center(child: CircularProgressIndicator());
    } else {
      final editable = order.status == OrderStatus.open;
      final browser = MenuBrowser(
        categories: menu,
        imageUrl: widget.menuApi.imageUrl,
        onItemTap: editable ? _addItem : null,
      );
      final panel = _OrderPanel(
        order: order,
        editable: editable,
        offline: _offline,
        sendState: _sendState,
        sentRound: _sentRound,
        onSend: _send,
        onQty: _setQty,
        onRemove: _remove,
        onNote: _editNote,
        onCancel: _cancel,
      );
      body = wide
          ? Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: browser),
                const VerticalDivider(width: 1),
                SizedBox(width: 400, child: panel),
              ],
            )
          : Column(
              children: [
                Expanded(flex: 5, child: browser),
                const Divider(height: 1),
                Expanded(flex: 4, child: panel),
              ],
            );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(order == null ? 'Order' : 'Table ${order.tableLabel}'),
        actions: [
          if (order != null)
            Padding(
              padding: const EdgeInsets.only(right: 16),
              child: Center(child: Text('Opened by ${order.waiter}')),
            ),
        ],
      ),
      body: Column(
        children: [
          if (_offline) const NoConnectionBanner(),
          if (order != null && order.status != OrderStatus.open)
            MaterialBanner(
              key: const Key('order-closed'),
              content: Text('This order is ${order.status.name} and can no longer be changed.'),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).maybePop(),
                  child: const Text('Back'),
                ),
              ],
            ),
          Expanded(child: body),
        ],
      ),
    );
  }
}

class _OrderPanel extends StatelessWidget {
  const _OrderPanel({
    required this.order,
    required this.editable,
    required this.offline,
    required this.sendState,
    required this.sentRound,
    required this.onSend,
    required this.onQty,
    required this.onRemove,
    required this.onNote,
    required this.onCancel,
  });

  final OrderData order;
  final bool editable;
  final bool offline;
  final _SendState sendState;
  final int? sentRound;
  final VoidCallback onSend;
  final void Function(OrderLineData line, int qty) onQty;
  final void Function(OrderLineData line) onRemove;
  final void Function(OrderLineData line) onNote;
  final void Function(OrderLineData line) onCancel;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    // Unsent lines first, as they are the ones being worked on.
    final lines = [
      ...order.lines.where((l) => l.status == LineStatus.unsent),
      ...order.lines.where((l) => l.status != LineStatus.unsent),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: lines.isEmpty
              ? const Center(child: Text('Tap items to add them.'))
              : ListView(
                  children: [
                    for (final line in lines)
                      _LineTile(
                        line: line,
                        editable: editable,
                        onQty: onQty,
                        onRemove: onRemove,
                        onNote: onNote,
                        onCancel: onCancel,
                      ),
                  ],
                ),
        ),
        const Divider(height: 1),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(
            children: [
              Text('Total', style: text.titleMedium),
              const Spacer(),
              Text('CHF ${order.total}', key: const Key('order-total'), style: text.titleLarge),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: SizedBox(height: 52, child: _sendButton()),
        ),
      ],
    );
  }

  Widget _sendButton() {
    final count = order.unsentCount;
    final canSend = editable && !offline && count > 0 && sendState != _SendState.sending;
    final (Widget icon, String label) = switch (sendState) {
      _SendState.sending => (
        const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)),
        'Sending…',
      ),
      _SendState.sent when count == 0 => (
        const Icon(Icons.check_circle),
        sentRound == null ? 'Nothing to send' : 'Sent · round $sentRound',
      ),
      _ => (const Icon(Icons.send), count == 0 ? 'Nothing to send' : 'Send ($count)'),
    };
    return FilledButton.icon(
      key: const Key('order-send'),
      onPressed: canSend ? onSend : null,
      icon: icon,
      label: Text(label),
    );
  }
}

class _LineTile extends StatelessWidget {
  const _LineTile({
    required this.line,
    required this.editable,
    required this.onQty,
    required this.onRemove,
    required this.onNote,
    required this.onCancel,
  });

  final OrderLineData line;
  final bool editable;
  final void Function(OrderLineData line, int qty) onQty;
  final void Function(OrderLineData line) onRemove;
  final void Function(OrderLineData line) onNote;
  final void Function(OrderLineData line) onCancel;

  @override
  Widget build(BuildContext context) {
    final cancelled = line.status == LineStatus.cancelled;
    final subtitle = [
      if (line.note != null) line.note!,
      if (line.status == LineStatus.sent) 'Sent · round ${line.roundNumber}',
      if (cancelled) 'Cancelled: ${line.cancelReason}',
    ].join('\n');

    return ListTile(
      key: Key('line-${line.id}'),
      leading: ItemTypeBadge(line.type),
      title: Text(
        line.status == LineStatus.unsent ? line.name : '${line.qty} × ${line.name}',
        style: cancelled
            ? const TextStyle(decoration: TextDecoration.lineThrough, color: Colors.grey)
            : null,
      ),
      subtitle: subtitle.isEmpty ? null : Text(subtitle),
      isThreeLine: subtitle.contains('\n'),
      trailing: switch (line.status) {
        LineStatus.unsent => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              key: Key('line-dec-${line.id}'),
              tooltip: 'One less',
              icon: const Icon(Icons.remove_circle_outline),
              onPressed: editable && line.qty > 1 ? () => onQty(line, line.qty - 1) : null,
            ),
            Text('${line.qty}', key: Key('line-qty-${line.id}')),
            IconButton(
              key: Key('line-inc-${line.id}'),
              tooltip: 'One more',
              icon: const Icon(Icons.add_circle_outline),
              onPressed: editable && line.qty < 99 ? () => onQty(line, line.qty + 1) : null,
            ),
            IconButton(
              key: Key('line-note-${line.id}'),
              tooltip: 'Note',
              icon: const Icon(Icons.edit_note),
              onPressed: editable ? () => onNote(line) : null,
            ),
            IconButton(
              key: Key('line-remove-${line.id}'),
              tooltip: 'Remove',
              icon: const Icon(Icons.delete_outline),
              onPressed: editable ? () => onRemove(line) : null,
            ),
          ],
        ),
        LineStatus.sent => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(line.lineTotal),
            IconButton(
              key: Key('line-cancel-${line.id}'),
              tooltip: 'Cancel line',
              icon: const Icon(Icons.block),
              onPressed: editable ? () => onCancel(line) : null,
            ),
          ],
        ),
        LineStatus.cancelled => Text(line.lineTotal),
      },
    );
  }
}

/// Asks for a note or a reason (at most 140 characters). Returns the text, or
/// null when dismissed.
class _TextDialog extends StatefulWidget {
  const _TextDialog({
    required this.title,
    required this.label,
    required this.action,
    this.initial,
    this.required = false,
  });

  final String title;
  final String label;
  final String action;
  final String? initial;
  final bool required;

  @override
  State<_TextDialog> createState() => _TextDialogState();
}

class _TextDialogState extends State<_TextDialog> {
  late final _text = TextEditingController(text: widget.initial);
  String? _error;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  void _submit() {
    final value = _text.text.trim();
    if (widget.required && value.isEmpty) {
      setState(() => _error = '${widget.label} is required');
      return;
    }
    Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        key: const Key('dialog-text'),
        controller: _text,
        autofocus: true,
        maxLength: 140,
        decoration: InputDecoration(labelText: widget.label, errorText: _error),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Back')),
        FilledButton(
          key: const Key('dialog-submit'),
          onPressed: _submit,
          child: Text(widget.action),
        ),
      ],
    );
  }
}
