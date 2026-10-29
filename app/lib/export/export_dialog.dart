import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../orders/order_history_screen.dart';
import 'export_api.dart';

/// Admin: pick a date range and order status, then download the CSV.
class ExportDialog extends StatefulWidget {
  const ExportDialog({super.key, required this.api, required this.initialDay});

  final ExportApi api;

  /// Preselected as start and end, e.g. the day shown in the history.
  final DateTime initialDay;

  @override
  State<ExportDialog> createState() => _ExportDialogState();
}

class _ExportDialogState extends State<ExportDialog> {
  late DateTime _from = widget.initialDay;
  late DateTime _to = widget.initialDay;
  ExportStatus _status = ExportStatus.sent;
  String? _error;
  bool _busy = false;

  Future<void> _pick({required bool start}) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: start ? _from : _to,
      firstDate: DateTime(2020),
      lastDate: DateTime.now(),
    );
    if (picked == null) return;
    setState(() {
      if (start) {
        _from = picked;
        if (_to.isBefore(picked)) _to = picked;
      } else {
        _to = picked;
        if (_from.isAfter(picked)) _from = picked;
      }
    });
  }

  Future<void> _download() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final name = await widget.api.downloadOrders(_from, _to, _status);
      if (mounted) Navigator.pop(context, name);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.fieldErrors.values.firstOrNull ?? e.message);
    } on UnsupportedError catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Export orders as CSV'),
      content: SizedBox(
        width: 380,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    key: const Key('export-from'),
                    onPressed: () => _pick(start: true),
                    child: Text('From ${formatDate(_from)}'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    key: const Key('export-to'),
                    onPressed: () => _pick(start: false),
                    child: Text('To ${formatDate(_to)}'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            SegmentedButton<ExportStatus>(
              key: const Key('export-status'),
              segments: const [
                ButtonSegment(value: ExportStatus.sent, label: Text('Sent')),
                ButtonSegment(value: ExportStatus.voided, label: Text('Voided')),
                ButtonSegment(value: ExportStatus.all, label: Text('All')),
              ],
              selected: {_status},
              onSelectionChanged: (s) => setState(() => _status = s.first),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(
                _error!,
                key: const Key('export-error'),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton.icon(
          key: const Key('export-download'),
          onPressed: _busy ? null : _download,
          icon: const Icon(Icons.download),
          label: const Text('Download'),
        ),
      ],
    );
  }
}
