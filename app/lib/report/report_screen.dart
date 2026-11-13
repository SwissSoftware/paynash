import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../customers/customer_models.dart';
import '../invoices/invoice_models.dart';
import '../invoices/invoices_screen.dart';
import 'report_api.dart';

/// The sales report for admins: a month (default the current one) or a date
/// range, with a summary and lists per day, service, and customer, and the
/// Excel export (spec sales-reporting, "Report on the devices").
class ReportScreen extends StatefulWidget {
  const ReportScreen({super.key, required this.api, this.now});

  final ReportApi api;

  /// Today; tests pass a fixed date.
  final DateTime Function()? now;

  @override
  State<ReportScreen> createState() => _ReportScreenState();
}

class _ReportScreenState extends State<ReportScreen> {
  late DateTime _from;
  late DateTime _to;

  /// A whole month is shown, so the arrows move by month.
  bool _monthMode = true;
  ReportData? _report;
  String? _error;
  bool _downloading = false;

  @override
  void initState() {
    super.initState();
    final now = (widget.now ?? DateTime.now)();
    _setMonth(DateTime(now.year, now.month));
    _load();
  }

  void _setMonth(DateTime month) {
    _from = DateTime(month.year, month.month);
    _to = DateTime(month.year, month.month + 1, 0);
    _monthMode = true;
  }

  Future<void> _load() async {
    setState(() => _report = null);
    try {
      final r = await widget.api.load(_from, _to);
      if (mounted) {
        setState(() {
          _report = r;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _shift(int months) {
    setState(() => _setMonth(DateTime(_from.year, _from.month + months)));
    _load();
  }

  Future<void> _pickRange() async {
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDateRange: DateTimeRange(start: _from, end: _to),
    );
    if (range == null) return;
    setState(() {
      _from = range.start;
      _to = range.end;
      _monthMode = false;
    });
    await _load();
  }

  Future<void> _excel() async {
    setState(() => _downloading = true);
    try {
      final name = await widget.api.downloadExcel(_from, _to);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Saved $name')));
      }
    } on ApiError catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  String get _periodLabel => _monthMode
      ? monthLabel(_from)
      : '${swissDate(_iso(_from))} – ${swissDate(_iso(_to))}';

  static String _iso(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final report = _report;
    return DefaultTabController(
      length: 3,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 8, 16, 0),
            child: Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 4,
              children: [
                IconButton(
                  key: const Key('report-prev'),
                  tooltip: 'Previous month',
                  icon: const Icon(Icons.chevron_left),
                  onPressed: () => _shift(-1),
                ),
                Text(_periodLabel, key: const Key('report-period')),
                IconButton(
                  key: const Key('report-next'),
                  tooltip: 'Next month',
                  icon: const Icon(Icons.chevron_right),
                  onPressed: () => _shift(1),
                ),
                TextButton.icon(
                  key: const Key('report-range'),
                  onPressed: _pickRange,
                  icon: const Icon(Icons.date_range),
                  label: const Text('Date range'),
                ),
                FilledButton.tonalIcon(
                  key: const Key('report-excel'),
                  onPressed: _downloading || report == null ? null : _excel,
                  icon: const Icon(Icons.table_view),
                  label: const Text('Excel'),
                ),
              ],
            ),
          ),
          if (_error != null)
            Expanded(child: Center(child: Text(_error!)))
          else if (report == null)
            const Expanded(child: Center(child: CircularProgressIndicator()))
          else ...[
            _SummaryCard(summary: report.summary),
            const TabBar(
              tabs: [
                Tab(key: Key('report-tab-days'), text: 'Days'),
                Tab(key: Key('report-tab-services'), text: 'Services'),
                Tab(key: Key('report-tab-customers'), text: 'Customers'),
              ],
            ),
            Expanded(
              child: TabBarView(
                children: [
                  ListView(
                    key: const Key('report-days'),
                    children: [
                      for (final d in report.days)
                        ListTile(
                          dense: true,
                          title: Text(swissDate(d.date)),
                          subtitle: Text('${d.invoiceCount} invoices'),
                          trailing: Text('CHF ${d.revenue}'),
                        ),
                    ],
                  ),
                  ListView(
                    key: const Key('report-services'),
                    children: [
                      for (final s in report.services)
                        ListTile(
                          dense: true,
                          title: Text(s.name),
                          subtitle: Text('${s.quantity} ×'),
                          trailing: Text('CHF ${s.revenue}'),
                        ),
                    ],
                  ),
                  ListView(
                    key: const Key('report-customers'),
                    children: [
                      for (final c in report.customers)
                        ListTile(
                          dense: true,
                          title: Text(c.name),
                          subtitle: Text('No. ${c.number} · ${c.invoiceCount} invoices'),
                          trailing: Text('CHF ${c.revenue}'),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.summary});

  final ReportSummary summary;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    Widget figure(String label, String value, String key) => Padding(
      padding: const EdgeInsets.only(right: 24, bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: text.labelMedium),
          Text(value, key: Key(key), style: text.titleLarge),
        ],
      ),
    );
    final paidBy = summary.byMethod.where((m) => m.amount != '0.00').map(
      (m) => '${PaymentMethodJson.parse(m.method).label} ${m.amount}',
    );
    return Card(
      key: const Key('report-summary'),
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              children: [
                figure('Revenue CHF', summary.revenue, 'report-revenue'),
                figure('Invoices', '${summary.invoiceCount}', 'report-count'),
                figure('Paid CHF', summary.paid, 'report-paid'),
                figure('Open CHF', summary.open, 'report-open'),
              ],
            ),
            if (paidBy.isNotEmpty) Text('Paid by: ${paidBy.join(' · ')}', key: const Key('report-methods')),
            for (final v in summary.vat)
              Text('VAT ${v.rate} %: ${v.vat} (net ${v.net}, gross ${v.gross})', style: text.bodySmall),
          ],
        ),
      ),
    );
  }
}
