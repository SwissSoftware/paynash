import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'customer_api.dart';
import 'customer_form.dart';
import 'customer_models.dart';

/// Opens an invoice, e.g. from a customer's history.
typedef OpenInvoice = void Function(BuildContext context, int invoiceId);

/// The salon's customers: search, create, and open a customer's history
/// (spec customer-management). Admins can also list inactive customers.
class CustomersScreen extends StatefulWidget {
  const CustomersScreen({super.key, required this.api, required this.isAdmin, this.openInvoice});

  final CustomerApi api;
  final bool isAdmin;
  final OpenInvoice? openInvoice;

  @override
  State<CustomersScreen> createState() => _CustomersScreenState();
}

class _CustomersScreenState extends State<CustomersScreen> {
  final _query = TextEditingController();
  Timer? _debounce;
  List<CustomerData>? _customers;
  String? _error;
  bool _inactive = false;

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
      final list = await widget.api.search(_query.text, includeInactive: _inactive);
      if (mounted) {
        setState(() {
          _customers = list;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _search(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _load);
  }

  Future<void> _create() async {
    final saved = await showDialog<CustomerData>(
      context: context,
      builder: (_) => CustomerFormDialog(api: widget.api),
    );
    if (saved != null && mounted) await _open(saved);
  }

  Future<void> _open(CustomerData customer) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => CustomerDetailScreen(
          api: widget.api,
          customerId: customer.id,
          isAdmin: widget.isAdmin,
          openInvoice: widget.openInvoice,
        ),
      ),
    );
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final customers = _customers;
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('customer-add'),
        onPressed: _create,
        icon: const Icon(Icons.person_add),
        label: const Text('New customer'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              key: const Key('customer-search'),
              controller: _query,
              onChanged: _search,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Name, number, phone, or email',
                border: OutlineInputBorder(),
              ),
            ),
          ),
          if (widget.isAdmin)
            SwitchListTile(
              key: const Key('customer-show-inactive'),
              title: const Text('Show inactive customers'),
              value: _inactive,
              onChanged: (v) {
                setState(() => _inactive = v);
                _load();
              },
            ),
          Expanded(
            child: _error != null
                ? Center(child: Text(_error!))
                : customers == null
                ? const Center(child: CircularProgressIndicator())
                : customers.isEmpty
                ? const Center(child: Text('No customers found.'))
                : ListView(
                    padding: const EdgeInsets.only(bottom: 88),
                    children: [
                      for (final c in customers)
                        ListTile(
                          key: Key('customer-${c.id}'),
                          leading: CircleAvatar(child: Text('${c.number}', maxLines: 1)),
                          title: Text(
                            c.name,
                            style: c.active
                                ? null
                                : const TextStyle(decoration: TextDecoration.lineThrough),
                          ),
                          subtitle: Text(c.active ? (c.contact ?? '') : 'inactive'),
                          onTap: () => _open(c),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

/// A customer with their invoices, newest first, and the total revenue.
/// Admins can deactivate, delete (or anonymize), and export the customer.
class CustomerDetailScreen extends StatefulWidget {
  const CustomerDetailScreen({
    super.key,
    required this.api,
    required this.customerId,
    required this.isAdmin,
    this.openInvoice,
  });

  final CustomerApi api;
  final int customerId;
  final bool isAdmin;
  final OpenInvoice? openInvoice;

  @override
  State<CustomerDetailScreen> createState() => _CustomerDetailScreenState();
}

class _CustomerDetailScreenState extends State<CustomerDetailScreen> {
  CustomerDetail? _detail;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final d = await widget.api.detail(widget.customerId);
      if (mounted) {
        setState(() {
          _detail = d;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
    } on ApiError catch (e) {
      if (mounted) _message(e.message);
    }
    await _load();
  }

  Future<void> _edit(CustomerData c) async {
    final saved = await showDialog<CustomerData>(
      context: context,
      builder: (_) => CustomerFormDialog(api: widget.api, customer: c),
    );
    if (saved != null) await _load();
  }

  Future<void> _delete(CustomerData c) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete ${c.name}?'),
        content: const Text(
          'A customer without orders is removed. A customer with orders is anonymized: name and '
          'contact details are removed, issued invoices keep their copy for the retention period.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            key: const Key('customer-delete-confirm'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      final result = await widget.api.delete(c.id);
      if (!mounted) return;
      if (result == 'deleted') {
        Navigator.pop(context);
        return;
      }
      _message('Customer anonymized');
    } on ApiError catch (e) {
      if (mounted) _message(e.message);
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final d = _detail;
    final text = Theme.of(context).textTheme;
    final c = d?.customer;
    return Scaffold(
      appBar: AppBar(
        title: Text(c?.name ?? 'Customer'),
        actions: [
          if (c != null && !c.anonymized)
            IconButton(
              key: const Key('customer-edit'),
              tooltip: 'Edit',
              icon: const Icon(Icons.edit),
              onPressed: () => _edit(c),
            ),
          if (c != null && widget.isAdmin)
            PopupMenuButton<String>(
              key: const Key('customer-admin-menu'),
              onSelected: (action) => switch (action) {
                'toggle' => _run(() => widget.api.setActive(c.id, !c.active)),
                'delete' => _delete(c),
                _ => _run(() async => _message('Saved ${await widget.api.export(c)}')),
              },
              itemBuilder: (_) => [
                if (!c.anonymized)
                  PopupMenuItem(
                    key: const Key('customer-toggle-active'),
                    value: 'toggle',
                    child: Text(c.active ? 'Deactivate' : 'Reactivate'),
                  ),
                const PopupMenuItem(
                  key: Key('customer-export'),
                  value: 'export',
                  child: Text('Export data'),
                ),
                const PopupMenuItem(
                  key: Key('customer-delete'),
                  value: 'delete',
                  child: Text('Delete'),
                ),
              ],
            ),
        ],
      ),
      body: _error != null
          ? Center(child: Text(_error!))
          : d == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text('Customer no. ${c!.number}${c.active ? '' : ' · inactive'}', style: text.labelLarge),
                for (final line in [
                  ?c.phone,
                  ?c.email,
                  ?c.street,
                  if (c.postalCode != null || c.city != null)
                    [?c.postalCode, ?c.city].join(' '),
                  ?c.note,
                ])
                  Text(line),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Text('Invoices', style: text.titleMedium),
                    const Spacer(),
                    Text('Revenue CHF ${d.revenue}', key: const Key('customer-revenue')),
                  ],
                ),
                if (d.invoices.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text('No invoices yet.'),
                  ),
                for (final i in d.invoices)
                  ListTile(
                    key: Key('customer-invoice-${i.number}'),
                    contentPadding: EdgeInsets.zero,
                    title: Text(i.number),
                    subtitle: Text(swissDate(i.date)),
                    trailing: Text('CHF ${i.total} · ${invoiceStatusLabel(i.status)}'),
                    onTap: widget.openInvoice == null
                        ? null
                        : () => widget.openInvoice!(context, i.id),
                  ),
              ],
            ),
    );
  }
}

/// "open", "paid", "cancelled" as shown to staff.
String invoiceStatusLabel(String status) => switch (status) {
  'paid' => 'paid',
  'cancelled' => 'cancelled',
  _ => 'open',
};
