import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../orders/order_history_screen.dart' show formatTime;
import '../orders/order_models.dart';
import '../orders/polling.dart';
import 'station_alerts.dart';
import 'station_api.dart';
import 'station_devices.dart';
import 'station_models.dart';
import 'station_order_screen.dart';

/// The screen next to a printer (spec print-station): the printer's status,
/// its tickets, and an alert with sound whenever a ticket or the printer needs
/// attention. Polls every 2 seconds and keeps the display on.
class StationScreen extends StatefulWidget {
  const StationScreen({super.key, required this.api, required this.devices});

  final StationApi api;
  final StationDevices devices;

  /// How often the alert sound repeats while an alert is active.
  static const beepEvery = Duration(seconds: 4);

  @override
  State<StationScreen> createState() => _StationScreenState();
}

class _StationScreenState extends State<StationScreen> with Polling {
  static const _interval = Duration(seconds: 2);

  List<StationPrinter>? _printers;
  int? _printerId;
  List<StationTicket>? _tickets;
  String? _error;
  bool _armed = false;
  bool _busy = false;

  /// Polls in a row that could not reach the server.
  int _failedPolls = 0;
  Timer? _beeper;

  @override
  Duration get pollEvery => _interval;

  @override
  void initState() {
    super.initState();
    widget.devices.keepAwake.enable();
    _beeper = Timer.periodic(StationScreen.beepEvery, (_) {
      if (_armed && _causes.isNotEmpty) widget.devices.sound.beep();
    });
    poll();
  }

  @override
  void dispose() {
    _beeper?.cancel();
    widget.devices.keepAwake.disable();
    super.dispose();
  }

  @override
  Future<void> poll() async {
    try {
      final printers = await widget.api.printers();
      final selected = printers.any((p) => p.id == _printerId)
          ? _printerId
          : printers.firstOrNull?.id;
      final tickets = selected == null ? <StationTicket>[] : await widget.api.tickets(selected);
      if (!mounted) return;
      setState(() {
        _printers = printers;
        _printerId = selected;
        _tickets = tickets;
        _failedPolls = 0;
        _error = null;
      });
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        if (e.isConnectionError) {
          _failedPolls++;
        } else {
          _error = e.message;
        }
      });
    }
  }

  List<String> get _causes =>
      alertCauses(_printers ?? const [], noConnectionFor: _interval * _failedPolls);

  Future<void> _arm() async {
    setState(() => _armed = true);
    await widget.devices.sound.arm();
  }

  Future<void> _run(Future<void> Function() op) async {
    setState(() => _busy = true);
    try {
      await op();
    } on ApiError catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    await poll();
  }

  Future<void> _openOrder(StationTicket ticket) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => StationOrderScreen(api: widget.api, orderId: ticket.orderId),
      ),
    );
    await poll();
  }

  void _select(int printerId) {
    setState(() {
      _printerId = printerId;
      _tickets = null;
    });
    poll();
  }

  @override
  Widget build(BuildContext context) {
    final printers = _printers;
    final tickets = _tickets;
    final causes = _causes;
    final colors = Theme.of(context).colorScheme;

    Widget list;
    if (_error != null && tickets == null) {
      list = Center(child: Text(_error!));
    } else if (printers == null || tickets == null) {
      list = const Center(child: CircularProgressIndicator());
    } else if (printers.isEmpty) {
      list = const Center(child: Text('No printer is assigned to this station.'));
    } else if (tickets.isEmpty) {
      list = const Center(child: Text('No tickets yet today.'));
    } else {
      list = ListView.separated(
        itemCount: tickets.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, i) => _TicketRow(
          ticket: tickets[i],
          enabled: !_busy,
          onOpen: () => _openOrder(tickets[i]),
          onWasPrinted: () => _run(() => widget.api.resolve(tickets[i].id, 'printed')),
          onResolveReprint: () => _run(() => widget.api.resolve(tickets[i].id, 'reprint')),
          onReprint: () => _run(() => widget.api.reprint(tickets[i].id)),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!_armed)
          Material(
            color: colors.tertiaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: FilledButton.icon(
                key: const Key('station-arm'),
                onPressed: _arm,
                icon: const Icon(Icons.volume_up),
                label: const Text('Tap to enable sound alerts'),
              ),
            ),
          ),
        if (causes.isNotEmpty)
          Material(
            key: const Key('station-alert'),
            color: colors.error,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                children: [
                  Icon(Icons.warning_amber, color: colors.onError, size: 32),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        for (final c in causes)
                          Text(
                            c,
                            style: Theme.of(context).textTheme.titleMedium
                                ?.copyWith(color: colors.onError),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        if (printers != null && printers.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final p in printers)
                  ChoiceChip(
                    key: Key('station-printer-${p.id}'),
                    selected: p.id == _printerId,
                    onSelected: (_) => _select(p.id),
                    avatar: Icon(
                      p.hasProblem ? Icons.print_disabled : Icons.print,
                      color: p.hasProblem ? colors.error : null,
                    ),
                    label: Text(
                      [
                        p.name,
                        p.status,
                        if (p.lastPrintedLabel != null) 'last ${p.lastPrintedLabel}',
                      ].join(' · '),
                    ),
                  ),
              ],
            ),
          ),
        const Divider(height: 1),
        Expanded(child: list),
      ],
    );
  }
}

class _TicketRow extends StatelessWidget {
  const _TicketRow({
    required this.ticket,
    required this.enabled,
    required this.onOpen,
    required this.onWasPrinted,
    required this.onResolveReprint,
    required this.onReprint,
  });

  final StationTicket ticket;
  final bool enabled;
  final VoidCallback onOpen;
  final VoidCallback onWasPrinted;
  final VoidCallback onResolveReprint;
  final VoidCallback onReprint;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final t = ticket;
    final kind = switch (t.kind) {
      TicketKind.normal => null,
      TicketKind.storno => 'STORNO',
      TicketKind.reprint => 'reprint',
    };
    final status = switch (t.status) {
      TicketStatus.pending when t.waitingReason != null => 'Waiting: ${t.waitingReason}',
      TicketStatus.pending => 'Waiting',
      TicketStatus.printing => 'Printing…',
      TicketStatus.printed => 'Printed',
      TicketStatus.unclear when t.needsResolution => 'UNCLEAR: ${t.error}',
      TicketStatus.unclear => 'Unclear, resolved',
      TicketStatus.failed => 'Failed: ${t.error}',
    };
    final alarming = t.needsResolution || (t.status == TicketStatus.failed && !t.reprinted);

    return Material(
      color: t.needsResolution ? colors.errorContainer : null,
      child: ListTile(
        key: Key('station-ticket-${t.id}'),
        onTap: enabled ? onOpen : null,
        leading: Text(formatTime(t.createdAt), style: Theme.of(context).textTheme.titleMedium),
        title: Text(
          [?t.label, 'Table ${t.tableLabel}', '#${t.orderId}', ?kind].join(' · '),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        subtitle: Text(
          '${t.waiter} · ${t.lines} ${t.lines == 1 ? 'line' : 'lines'} · $status',
          key: Key('station-ticket-status-${t.id}'),
          style: alarming ? TextStyle(color: colors.error, fontWeight: FontWeight.bold) : null,
        ),
        trailing: t.needsResolution
            ? Wrap(
                spacing: 8,
                children: [
                  FilledButton(
                    key: Key('station-was-printed-${t.id}'),
                    onPressed: enabled ? onWasPrinted : null,
                    child: const Text('Was printed'),
                  ),
                  OutlinedButton(
                    key: Key('station-resolve-reprint-${t.id}'),
                    onPressed: enabled ? onResolveReprint : null,
                    child: const Text('Reprint'),
                  ),
                ],
              )
            : TextButton.icon(
                key: Key('station-reprint-${t.id}'),
                onPressed: enabled && !t.inProgress ? onReprint : null,
                icon: const Icon(Icons.print),
                label: const Text('Reprint'),
              ),
      ),
    );
  }
}
