import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../admin/tables_api.dart';
import '../api/api_client.dart';
import '../menu/item_visuals.dart';
import '../menu/menu_api.dart';
import '../menu/menu_browser.dart';
import '../menu/menu_models.dart';
import 'order_api.dart';
import 'order_models.dart';
import 'polling.dart';
import 'text_dialog.dart';

final _tableLabel = RegExp(r'^[A-Za-z0-9]{1,10}$');

/// Taking an order: pick or type the table, tap menu tiles to build the order on
/// this device, adjust quantities and notes, and send it in one go. After a
/// successful send the screen is empty again; the order is in the history.
class OrderScreen extends StatefulWidget {
  const OrderScreen({
    super.key,
    required this.orderApi,
    required this.menuApi,
    required this.tablesApi,
  });

  final OrderApi orderApi;
  final MenuApi menuApi;
  final TablesApi tablesApi;

  @override
  State<OrderScreen> createState() => _OrderScreenState();
}

class _OrderScreenState extends State<OrderScreen> with Polling {
  final _table = TextEditingController();
  final _lines = <DraftLine>[];
  List<MenuCategoryData>? _menu;
  List<TableData> _tables = const [];
  String? _error;
  String? _tableError;

  /// Why the last send failed. Shown next to the send button rather than in a
  /// snack bar, which would cover the button needed to try again.
  String? _sendError;
  bool _offline = false;
  bool _sending = false;

  /// Kept while the draft is unchanged, so retrying a send whose answer was
  /// lost cannot create a second order (design D5).
  String? _sendKey;

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

  /// Keeps the menu and the tables current and notices when the server is
  /// unreachable.
  @override
  Future<void> poll() async {
    try {
      final menu = await widget.menuApi.load();
      final tables = await widget.tablesApi.active();
      if (mounted) {
        setState(() {
          _menu = menu;
          _tables = tables;
          _offline = false;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _offline = e.isConnectionError;
        if (!e.isConnectionError || _menu == null) _error = e.message;
      });
    }
  }

  /// Applies a change to the draft. Any change means a new order for the
  /// server, so it needs a new idempotency key.
  void _edit(VoidCallback change) {
    // The confirmation of the previous order would cover the send button.
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    setState(() {
      change();
      _sendKey = null;
      _sendError = null;
    });
  }

  void _addItem(MenuItemData item) {
    _edit(() {
      // Tapping a tile again raises the quantity of its line, unless that line
      // has a note (then it is a different request from the guest).
      final existing = _lines.where((l) => l.item.id == item.id && l.note == null && l.qty < 99);
      if (existing.isEmpty) {
        _lines.add(DraftLine(item));
      } else {
        existing.first.qty++;
      }
    });
  }

  void _pickTable(TableData table) {
    _edit(() {
      _table.text = table.number;
      _tableError = null;
    });
  }

  Future<void> _editNote(DraftLine line) async {
    final note = await showDialog<String>(
      context: context,
      builder: (_) => TextDialog(
        title: 'Note for ${line.item.name}',
        label: 'Note',
        initial: line.note,
        action: 'Save',
      ),
    );
    if (note == null) return;
    _edit(() => line.note = note.isEmpty ? null : note);
  }

  Future<void> _send() async {
    final table = _table.text.trim();
    if (!_tableLabel.hasMatch(table)) {
      setState(() => _tableError = '1 to 10 letters or digits');
      return;
    }
    setState(() {
      _tableError = null;
      _sendError = null;
      _sending = true;
    });
    final key = _sendKey ??= newIdempotencyKey();
    try {
      final order = await widget.orderApi.send(table, List.of(_lines), key);
      if (!mounted) return;
      setState(() {
        _lines.clear();
        _table.clear();
        _sendKey = null;
        _offline = false;
      });
      _message('Order for table ${order.tableLabel} sent');
    } on ApiError catch (e) {
      if (!mounted) return;
      final inactive = (e.details['inactiveMenuItemIds'] as List?)?.cast<int>();
      if (inactive != null) {
        _edit(() => _lines.removeWhere((l) => inactive.contains(l.item.id)));
        await poll();
      }
      setState(() {
        if (e.isConnectionError) _offline = true;
        if (e.fieldErrors['tableLabel'] != null) {
          _tableError = e.fieldErrors['tableLabel'];
        } else {
          _sendError = e.isConnectionError
              ? 'Not sent: no connection. Please try again.'
              : e.message;
        }
      });
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  @override
  Widget build(BuildContext context) {
    final menu = _menu;
    final wide = MediaQuery.sizeOf(context).width >= 720;

    Widget body;
    if (_error != null && menu == null) {
      body = Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            TextButton(
              onPressed: () {
                setState(() => _error = null);
                poll();
              },
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    } else if (menu == null) {
      body = const Center(child: CircularProgressIndicator());
    } else {
      final browser = MenuBrowser(
        categories: menu,
        imageUrl: widget.menuApi.imageUrl,
        onItemTap: _sending ? null : _addItem,
      );
      final panel = _DraftPanel(
        compact: !wide,
        table: _table,
        tables: _tables,
        onPickTable: _pickTable,
        // A different table is a different order, like a changed line.
        onTableChanged: () => _edit(() {}),
        tableError: _tableError,
        sendError: _sendError,
        lines: _lines,
        offline: _offline,
        sending: _sending,
        onSend: _send,
        onQty: (line, qty) => _edit(() => line.qty = qty),
        onRemove: (line) => _edit(() => _lines.remove(line)),
        onNote: _editNote,
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

    return Column(
      children: [
        if (_offline) const NoConnectionBanner(),
        Expanded(child: body),
      ],
    );
  }
}

class _DraftPanel extends StatelessWidget {
  const _DraftPanel({
    required this.compact,
    required this.table,
    required this.tables,
    required this.onPickTable,
    required this.onTableChanged,
    required this.tableError,
    required this.sendError,
    required this.lines,
    required this.offline,
    required this.sending,
    required this.onSend,
    required this.onQty,
    required this.onRemove,
    required this.onNote,
  });

  /// Phone layout: the panel is short, so the table field and the table
  /// chips share one row.
  final bool compact;
  final TextEditingController table;
  final List<TableData> tables;
  final void Function(TableData table) onPickTable;
  final VoidCallback onTableChanged;
  final String? tableError;
  final String? sendError;
  final List<DraftLine> lines;
  final bool offline;
  final bool sending;
  final VoidCallback onSend;
  final void Function(DraftLine line, int qty) onQty;
  final void Function(DraftLine line) onRemove;
  final void Function(DraftLine line) onNote;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final total = lines.fold(0, (sum, l) => sum + l.totalRappen);
    final count = lines.fold(0, (sum, l) => sum + l.qty);
    final tableField = TextField(
      key: const Key('table-input'),
      controller: table,
      enabled: !sending,
      onChanged: (_) => onTableChanged(),
      decoration: InputDecoration(
        labelText: 'Table',
        errorText: tableError,
        prefixIcon: compact ? null : const Icon(Icons.table_restaurant),
        border: const OutlineInputBorder(),
      ),
      textCapitalization: TextCapitalization.characters,
      inputFormatters: [LengthLimitingTextInputFormatter(10)],
    );
    final picker = tables.isEmpty
        ? null
        : _TablePicker(
            tables: tables,
            selected: table,
            singleRow: compact,
            onPick: sending ? null : onPickTable,
          );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (compact && picker != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 0, 4),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: 110, child: tableField),
                const SizedBox(width: 8),
                Expanded(child: picker),
              ],
            ),
          )
        else ...[
          ?picker,
          Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 4), child: tableField),
        ],
        Expanded(
          child: lines.isEmpty
              ? const Center(child: Text('Tap items to add them.'))
              : ListView(
                  children: [
                    for (final (i, line) in lines.indexed)
                      _DraftLineTile(
                        index: i,
                        line: line,
                        enabled: !sending,
                        onQty: onQty,
                        onRemove: onRemove,
                        onNote: onNote,
                      ),
                  ],
                ),
        ),
        const Divider(height: 1),
        if (sendError != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Text(
              sendError!,
              key: const Key('send-error'),
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(
            children: [
              Text('Total', style: text.titleMedium),
              const Spacer(),
              Text('CHF ${chf(total)}', key: const Key('order-total'), style: text.titleLarge),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: SizedBox(
            height: 52,
            child: FilledButton.icon(
              key: const Key('order-send'),
              onPressed: !offline && !sending && lines.isNotEmpty ? onSend : null,
              icon: sending
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.send),
              label: Text(
                sending ? 'Sending…' : (lines.isEmpty ? 'Nothing to send' : 'Send ($count)'),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// The configured tables as chips. The table field stays as the fallback;
/// typing a table's number selects its chip as well. Many tables scroll
/// instead of pushing the order lines and the send button off the screen.
class _TablePicker extends StatelessWidget {
  const _TablePicker({
    required this.tables,
    required this.selected,
    required this.singleRow,
    required this.onPick,
  });

  final List<TableData> tables;
  final TextEditingController selected;

  /// One horizontally scrolling row (phone) instead of wrapping rows.
  final bool singleRow;
  final void Function(TableData table)? onPick;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ValueListenableBuilder(
      valueListenable: selected,
      builder: (context, value, _) {
        final current = value.text.trim().toUpperCase();
        final chips = [
          for (final t in tables)
            ChoiceChip(
              key: Key('table-chip-${t.number}'),
              selected: t.number == current,
              showCheckmark: false,
              onSelected: onPick == null ? null : (_) => onPick!(t),
              label: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(t.number, style: text.titleMedium),
                  if (t.name != null) Text(t.name!, style: text.labelSmall),
                ],
              ),
            ),
        ];
        if (singleRow) {
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.only(right: 16),
            child: Row(spacing: 8, children: chips),
          );
        }
        return ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 150),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Wrap(spacing: 8, runSpacing: 8, children: chips),
          ),
        );
      },
    );
  }
}

class _DraftLineTile extends StatelessWidget {
  const _DraftLineTile({
    required this.index,
    required this.line,
    required this.enabled,
    required this.onQty,
    required this.onRemove,
    required this.onNote,
  });

  /// Position in the draft; draft lines have no server id yet.
  final int index;
  final DraftLine line;
  final bool enabled;
  final void Function(DraftLine line, int qty) onQty;
  final void Function(DraftLine line) onRemove;
  final void Function(DraftLine line) onNote;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: Key('draft-line-$index'),
      leading: ItemTypeBadge(line.item.type),
      title: Text(line.item.name),
      subtitle: line.note == null ? null : Text(line.note!),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            key: Key('line-dec-$index'),
            tooltip: 'One less',
            icon: const Icon(Icons.remove_circle_outline),
            onPressed: enabled && line.qty > 1 ? () => onQty(line, line.qty - 1) : null,
          ),
          Text('${line.qty}', key: Key('line-qty-$index')),
          IconButton(
            key: Key('line-inc-$index'),
            tooltip: 'One more',
            icon: const Icon(Icons.add_circle_outline),
            onPressed: enabled && line.qty < 99 ? () => onQty(line, line.qty + 1) : null,
          ),
          IconButton(
            key: Key('line-note-$index'),
            tooltip: 'Note',
            icon: const Icon(Icons.edit_note),
            onPressed: enabled ? () => onNote(line) : null,
          ),
          IconButton(
            key: Key('line-remove-$index'),
            tooltip: 'Remove',
            icon: const Icon(Icons.delete_outline),
            onPressed: enabled ? () => onRemove(line) : null,
          ),
        ],
      ),
    );
  }
}
