import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../customers/customer_models.dart';
import 'invoice_api.dart';
import 'invoice_models.dart';
import 'invoice_screen.dart';

/// Invoices of a month, newest first, filterable by status and searchable by
/// number and customer (spec invoicing, "Invoice list").
class InvoicesScreen extends StatefulWidget {
  const InvoicesScreen({super.key, required this.api, required this.isAdmin, this.now});

  final InvoiceApi api;
  final bool isAdmin;

  /// Today; tests pass a fixed date.
  final DateTime Function()? now;

  @override
  State<InvoicesScreen> createState() => _InvoicesScreenState();
}

class _InvoicesScreenState extends State<InvoicesScreen> {
  late DateTime _month = _firstOfMonth((widget.now ?? DateTime.now)());
  InvoiceStatus? _status;
  final _query = TextEditingController();
  Timer? _debounce;
  List<InvoiceSummary>? _invoices;
  String? _error;

  static DateTime _firstOfMonth(DateTime d) => DateTime(d.year, d.month);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _query.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final list = await widget.api.list(
        from: _month,
        to: DateTime(_month.year, _month.month + 1, 0),
        status: _status,
        query: _query.text,
      );
      if (mounted) {
        setState(() {
          _invoices = list;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _shiftMonth(int delta) {
    setState(() => _month = DateTime(_month.year, _month.month + delta));
    _load();
  }

  Future<void> _open(int id) async {
    await openInvoice(context, widget.api, id, isAdmin: widget.isAdmin);
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final invoices = _invoices;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 16, 0),
          child: Row(
            children: [
              IconButton(
                key: const Key('invoices-prev-month'),
                tooltip: 'Previous month',
                icon: const Icon(Icons.chevron_left),
                onPressed: () => _shiftMonth(-1),
              ),
              Text(monthLabel(_month), key: const Key('invoices-month')),
              IconButton(
                key: const Key('invoices-next-month'),
                tooltip: 'Next month',
                icon: const Icon(Icons.chevron_right),
                onPressed: () => _shiftMonth(1),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  key: const Key('invoices-search'),
                  controller: _query,
                  onChanged: (_) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 300), _load);
                  },
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: 'Number or customer',
                  ),
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Wrap(
            spacing: 8,
            children: [
              for (final s in [null, ...InvoiceStatus.values])
                ChoiceChip(
                  key: Key('invoices-filter-${s?.name ?? 'all'}'),
                  label: Text(s?.name ?? 'all'),
                  selected: _status == s,
                  onSelected: (_) {
                    setState(() => _status = s);
                    _load();
                  },
                ),
            ],
          ),
        ),
        Expanded(
          child: _error != null
              ? Center(child: Text(_error!))
              : invoices == null
              ? const Center(child: CircularProgressIndicator())
              : invoices.isEmpty
              ? const Center(child: Text('No invoices.'))
              : ListView(
                  children: [
                    for (final i in invoices)
                      ListTile(
                        key: Key('invoice-${i.number}'),
                        title: Text('${i.number} · ${i.customerName}'),
                        subtitle: Text(swissDate(i.date)),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text('CHF ${i.total}'),
                            const SizedBox(width: 8),
                            InvoiceStatusChip(i.status),
                          ],
                        ),
                        onTap: () => _open(i.id),
                      ),
                  ],
                ),
        ),
      ],
    );
  }
}

const _months = [
  'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September',
  'October', 'November', 'December',
];

/// "November 2026".
String monthLabel(DateTime month) => '${_months[month.month - 1]} ${month.year}';

class InvoiceStatusChip extends StatelessWidget {
  const InvoiceStatusChip(this.status, {super.key});

  final InvoiceStatus status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (status) {
      InvoiceStatus.open => scheme.tertiary,
      InvoiceStatus.paid => Colors.green.shade700,
      InvoiceStatus.cancelled => scheme.error,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(status.name, style: TextStyle(color: color)),
    );
  }
}
