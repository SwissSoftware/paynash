import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../customers/customer_models.dart';
import '../orders/text_dialog.dart';
import 'invoice_api.dart';
import 'invoice_models.dart';
import 'invoices_screen.dart';

/// Opens an invoice on its own page.
Future<void> openInvoice(BuildContext context, InvoiceApi api, int id, {required bool isAdmin}) =>
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => InvoiceScreen(api: api, invoiceId: id, isAdmin: isAdmin),
      ),
    );

/// An invoice with its receipt status and actions (spec invoicing): print a
/// copy, download the PDF, mark it paid; admins also cancel and undo payments.
class InvoiceScreen extends StatefulWidget {
  const InvoiceScreen({
    super.key,
    required this.api,
    required this.invoiceId,
    required this.isAdmin,
    this.now,
  });

  final InvoiceApi api;
  final int invoiceId;
  final bool isAdmin;

  /// Today; tests pass a fixed date.
  final DateTime Function()? now;

  @override
  State<InvoiceScreen> createState() => _InvoiceScreenState();
}

class _InvoiceScreenState extends State<InvoiceScreen> {
  InvoiceData? _invoice;
  String? _error;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final i = await widget.api.get(widget.invoiceId);
      if (mounted) {
        setState(() {
          _invoice = i;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  Future<void> _run(Future<String?> Function() action) async {
    setState(() => _busy = true);
    try {
      final done = await action();
      if (done != null && mounted) _message(done);
    } on ApiError catch (e) {
      if (mounted) _message(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    await _load();
  }

  Future<String?> _reason(String title, String action) => showDialog<String>(
    context: context,
    builder: (_) => TextDialog(title: title, label: 'Reason', action: action, required: true),
  );

  Future<void> _pay(InvoiceData invoice) async {
    final today = (widget.now ?? DateTime.now)();
    final result = await showDialog<(PaymentMethod, DateTime)>(
      context: context,
      builder: (_) => _PaymentDialog(
        today: DateTime(today.year, today.month, today.day),
        invoiceDate: DateTime.parse(invoice.date),
      ),
    );
    if (result == null) return;
    await _run(() async {
      await widget.api.pay(invoice.id, result.$1, result.$2);
      return 'Marked as paid';
    });
  }

  Future<void> _cancel(InvoiceData invoice) async {
    final reason = await _reason('Cancel invoice ${invoice.number}?', 'Cancel invoice');
    if (reason == null) return;
    await _run(() async {
      await widget.api.cancel(invoice.id, reason);
      return 'Invoice cancelled';
    });
  }

  Future<void> _undo(InvoiceData invoice) async {
    final reason = await _reason('Set ${invoice.number} back to open?', 'Undo payment');
    if (reason == null) return;
    await _run(() async {
      await widget.api.undoPayment(invoice.id, reason);
      return 'Payment removed';
    });
  }

  @override
  Widget build(BuildContext context) {
    final i = _invoice;
    return Scaffold(
      appBar: AppBar(title: Text(i == null ? 'Invoice' : 'Invoice ${i.number}')),
      body: _error != null && i == null
          ? Center(child: Text(_error!))
          : i == null
          ? const Center(child: CircularProgressIndicator())
          : _content(context, i),
    );
  }

  Widget _content(BuildContext context, InvoiceData i) {
    final text = Theme.of(context).textTheme;
    final error = Theme.of(context).colorScheme.error;
    final receipt = i.receipt;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            Expanded(child: Text('${i.number} · ${swissDate(i.date)}', style: text.titleLarge)),
            InvoiceStatusChip(i.status),
          ],
        ),
        if (i.status == InvoiceStatus.cancelled)
          Text(
            'Cancelled${i.cancelledBy == null ? '' : ' by ${i.cancelledBy}'}: ${i.cancelReason}',
            style: TextStyle(color: error),
          ),
        const SizedBox(height: 8),
        Text('Customer no. ${i.customerNumber}', style: text.labelLarge),
        Text(i.customerName),
        for (final line in i.customerAddress) Text(line),
        const Divider(height: 24),
        for (final l in i.lines)
          ListTile(
            contentPadding: EdgeInsets.zero,
            dense: true,
            title: Text('${l.qty} × ${l.name}'),
            subtitle: l.note == null ? null : Text(l.note!),
            trailing: Text(l.amount),
          ),
        const Divider(),
        Row(
          children: [
            Text('Total CHF', style: text.titleMedium),
            const Spacer(),
            Text(i.total, key: const Key('invoice-total'), style: text.titleMedium),
          ],
        ),
        if (i.vatEnabled) ...[
          const SizedBox(height: 8),
          Text('VAT no. ${i.vatUid}', style: text.bodySmall),
          for (final v in i.vat)
            Text('VAT ${v.rate} %: ${v.vat} (net ${v.net})', style: text.bodySmall),
        ],
        const SizedBox(height: 16),
        if (i.payment != null)
          Text(
            'Paid on ${swissDate(i.payment!.paidOn)} by ${i.payment!.method.label} '
            '(recorded by ${i.payment!.recordedBy})',
            key: const Key('invoice-payment'),
          ),
        for (final p in i.paymentHistory.where((p) => p.removed))
          Text(
            'Removed payment: ${p.method.label}, ${swissDate(p.paidOn)} – ${p.removeReason} '
            '(${p.removedBy})',
            style: text.bodySmall,
          ),
        if (receipt != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              receipt.text,
              key: const Key('invoice-receipt-status'),
              style: receipt.needsAttention ? TextStyle(color: error) : null,
            ),
          ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              key: const Key('invoice-print-copy'),
              onPressed: _busy || (receipt?.inProgress ?? false)
                  ? null
                  : () => _run(() async {
                      await widget.api.printCopy(i.id);
                      return 'Copy sent to the receipt printer';
                    }),
              icon: const Icon(Icons.print),
              label: const Text('Print copy'),
            ),
            OutlinedButton.icon(
              key: const Key('invoice-pdf'),
              onPressed: _busy
                  ? null
                  : () => _run(() async => 'Saved ${await widget.api.downloadPdf(i)}'),
              icon: const Icon(Icons.picture_as_pdf),
              label: const Text('Download PDF'),
            ),
            if (i.status == InvoiceStatus.open)
              FilledButton.icon(
                key: const Key('invoice-pay'),
                onPressed: _busy ? null : () => _pay(i),
                icon: const Icon(Icons.payments),
                label: const Text('Mark as paid'),
              ),
            if (widget.isAdmin && i.status == InvoiceStatus.paid)
              TextButton(
                key: const Key('invoice-undo-payment'),
                onPressed: _busy ? null : () => _undo(i),
                child: const Text('Undo payment'),
              ),
            if (widget.isAdmin && i.status == InvoiceStatus.open)
              TextButton(
                key: const Key('invoice-cancel'),
                onPressed: _busy ? null : () => _cancel(i),
                style: TextButton.styleFrom(foregroundColor: error),
                child: const Text('Cancel invoice'),
              ),
          ],
        ),
      ],
    );
  }
}

/// Payment method and date; the date lies between the invoice date and today.
class _PaymentDialog extends StatefulWidget {
  const _PaymentDialog({required this.today, required this.invoiceDate});

  final DateTime today;
  final DateTime invoiceDate;

  @override
  State<_PaymentDialog> createState() => _PaymentDialogState();
}

class _PaymentDialogState extends State<_PaymentDialog> {
  PaymentMethod? _method;
  late DateTime _date = widget.today;

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: widget.invoiceDate,
      lastDate: widget.today,
    );
    if (picked != null) setState(() => _date = picked);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Mark as paid'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final m in PaymentMethod.values)
                ChoiceChip(
                  key: Key('payment-${m.json}'),
                  label: Text(m.label),
                  selected: _method == m,
                  onSelected: (_) => setState(() => _method = m),
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextButton.icon(
            key: const Key('payment-date'),
            onPressed: _pickDate,
            icon: const Icon(Icons.event),
            label: Text(swissDate(isoDate(_date))),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          key: const Key('payment-save'),
          onPressed: _method == null ? null : () => Navigator.pop(context, (_method!, _date)),
          child: const Text('Save'),
        ),
      ],
    );
  }
}
