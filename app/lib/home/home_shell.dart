import 'package:flutter/material.dart';

import '../admin/menu_admin_api.dart';
import '../admin/menu_admin_screen.dart';
import '../admin/printers_api.dart';
import '../admin/printers_screen.dart';
import '../admin/staff_api.dart';
import '../admin/staff_screen.dart';
import '../admin/tables_api.dart';
import '../admin/tables_screen.dart';
import '../auth/session.dart';
import '../customers/customer_api.dart';
import '../customers/customers_screen.dart';
import '../export/export_api.dart';
import '../invoices/invoice_api.dart';
import '../invoices/invoice_screen.dart';
import '../invoices/invoices_screen.dart';
import '../menu/menu_api.dart';
import '../menu/menu_screen.dart';
import '../orders/order_api.dart';
import '../orders/order_history_screen.dart';
import '../orders/order_screen.dart';
import '../report/report_api.dart';
import '../report/report_screen.dart';
import '../settings/settings_api.dart';
import '../settings/settings_controller.dart';
import '../settings/settings_models.dart';
import '../settings/settings_screen.dart';
import '../station/station_api.dart';
import '../station/station_devices.dart';
import '../station/station_screen.dart';

/// Main layout after login. Admin-only sections are hidden for waiters
/// (the backend enforces the same rules). The sections follow the
/// installation mode: tables and stations in event mode; customers, invoices,
/// and the sales report in salon mode (spec venue-mode, "Functions per mode").
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.session, required this.stationDevices});

  final SessionController session;
  final StationDevices stationDevices;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _Section {
  const _Section(this.label, this.icon, this.build);
  final String label;
  final IconData icon;
  final Widget Function() build;
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  int _index = 0;
  late final _menuApi = MenuApi(widget.session.api);
  late final _orderApi = OrderApi(widget.session.api);
  late final _tablesApi = TablesApi(widget.session.api);
  late final _settings = SettingsController(SettingsApi(widget.session.api));
  late final _customerApi = CustomerApi(widget.session.api);
  late final _invoiceApi = InvoiceApi(widget.session.api);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _settings.load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _settings.dispose();
    super.dispose();
  }

  /// A mode change by an admin reaches this device on its next resume.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _settings.load();
  }

  List<_Section> _sections(Me me, AppSettings settings) {
    if (me.isStation) return _stationSections();
    final terms = settings.terms;
    final salon = settings.isSalon;
    return [
      _Section('New order', Icons.add_shopping_cart,
          () => OrderScreen(
                orderApi: _orderApi,
                menuApi: _menuApi,
                tablesApi: _tablesApi,
                customerApi: salon ? _customerApi : null,
                invoiceApi: salon ? _invoiceApi : null,
              )),
      _Section('History', Icons.receipt_long,
          () => OrderHistoryScreen(
                orderApi: _orderApi,
                isAdmin: me.isAdmin,
                exportApi: me.isAdmin && !salon ? ExportApi(widget.session.api) : null,
                invoiceApi: salon ? _invoiceApi : null,
              )),
      if (salon)
        _Section('Customers', Icons.people,
            () => CustomersScreen(
                  api: _customerApi,
                  isAdmin: me.isAdmin,
                  openInvoice: (context, id) =>
                      openInvoice(context, _invoiceApi, id, isAdmin: me.isAdmin),
                )),
      if (salon)
        _Section('Invoices', Icons.request_quote,
            () => InvoicesScreen(api: _invoiceApi, isAdmin: me.isAdmin)),
      _Section(terms.items, salon ? Icons.spa : Icons.restaurant_menu,
          () => MenuScreen(menuApi: _menuApi)),
      if (me.isAdmin && salon)
        _Section('Report', Icons.insights, () => ReportScreen(api: ReportApi(widget.session.api))),
      if (me.isAdmin)
        _Section('${terms.items} setup', Icons.edit_note,
            () => MenuAdminScreen(
                  api: MenuAdminApi(widget.session.api),
                  mode: settings.mode,
                  vatEnabled: settings.vatEnabled,
                )),
      if (me.isAdmin && !salon)
        _Section('Tables', Icons.table_restaurant, () => TablesScreen(api: _tablesApi)),
      if (me.isAdmin)
        _Section('Printers', Icons.print,
            () => PrintersScreen(api: PrintersApi(widget.session.api))),
      if (me.isAdmin)
        _Section('Staff', Icons.badge,
            () => StaffScreen(api: StaffApi(widget.session.api), me: me, terms: terms)),
      if (me.isAdmin)
        _Section('Settings', Icons.settings,
            () => SettingsScreen(api: _settings.api, controller: _settings)),
    ];
  }

  /// A station sees only the station view (spec staff-access, station permissions).
  List<_Section> _stationSections() => [
        _Section('Station', Icons.print,
            () => StationScreen(api: StationApi(widget.session.api), devices: widget.stationDevices)),
      ];

  @override
  Widget build(BuildContext context) =>
      ListenableBuilder(listenable: _settings, builder: (context, _) => _build(context));

  Widget _build(BuildContext context) {
    final me = widget.session.me!;
    final settings = _settings.settings;
    if (settings == null) return _loading(me);
    final sections = _sections(me, settings);
    final index = _index.clamp(0, sections.length - 1);
    final wide = MediaQuery.sizeOf(context).width >= 720;

    return Scaffold(
      appBar: AppBar(
        title: Text(sections[index].label),
        actions: [
          Center(child: Text(me.displayName, key: const Key('current-user'))),
          IconButton(
            key: const Key('logout'),
            tooltip: 'Log out',
            icon: const Icon(Icons.logout),
            onPressed: widget.session.logout,
          ),
        ],
      ),
      body: Row(
        children: [
          if (wide && sections.length > 1)
            NavigationRail(
              selectedIndex: index,
              labelType: NavigationRailLabelType.all,
              onDestinationSelected: (i) => setState(() => _index = i),
              destinations: [
                for (final s in sections)
                  NavigationRailDestination(icon: Icon(s.icon), label: Text(s.label)),
              ],
            ),
          Expanded(child: sections[index].build()),
        ],
      ),
      bottomNavigationBar: !wide && sections.length > 1 ? _bottomBar(sections, index) : null,
    );
  }

  /// Before the settings are known the sections cannot be built.
  Widget _loading(Me me) => Scaffold(
        appBar: AppBar(
          actions: [
            IconButton(
              key: const Key('logout'),
              tooltip: 'Log out',
              icon: const Icon(Icons.logout),
              onPressed: widget.session.logout,
            ),
          ],
        ),
        body: Center(
          child: _settings.error == null
              ? const CircularProgressIndicator()
              : Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(_settings.error!, key: const Key('settings-error')),
                    TextButton(onPressed: _settings.load, child: const Text('Retry')),
                  ],
                ),
        ),
      );

  /// Phone navigation. More than [_maxBarItems] sections do not fit (the labels
  /// wrap), so the waiter sections stay in the bar and the admin sections move
  /// into "More".
  static const _maxBarItems = 4;
  static const _barSections = _maxBarItems - 1;

  Widget _bottomBar(List<_Section> sections, int index) {
    final overflow = sections.length > _maxBarItems;
    final inBar = overflow ? sections.take(_barSections).toList() : sections;
    return NavigationBar(
      selectedIndex: overflow && index >= _barSections ? _barSections : index,
      onDestinationSelected: (i) {
        if (overflow && i == _barSections) {
          _showMore(sections, index);
        } else {
          setState(() => _index = i);
        }
      },
      destinations: [
        for (final s in inBar) NavigationDestination(icon: Icon(s.icon), label: s.label),
        if (overflow)
          const NavigationDestination(
            key: Key('nav-more'),
            icon: Icon(Icons.more_horiz),
            label: 'More',
          ),
      ],
    );
  }

  Future<void> _showMore(List<_Section> sections, int index) async {
    final picked = await showModalBottomSheet<int>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = _barSections; i < sections.length; i++)
              ListTile(
                leading: Icon(sections[i].icon),
                title: Text(sections[i].label),
                selected: i == index,
                onTap: () => Navigator.pop(context, i),
              ),
          ],
        ),
      ),
    );
    if (picked != null) setState(() => _index = picked);
  }
}
