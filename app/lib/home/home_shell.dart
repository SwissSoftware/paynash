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
import '../export/export_api.dart';
import '../menu/menu_api.dart';
import '../menu/menu_screen.dart';
import '../orders/order_api.dart';
import '../orders/order_history_screen.dart';
import '../orders/order_screen.dart';
import '../station/station_api.dart';
import '../station/station_devices.dart';
import '../station/station_screen.dart';

/// Main layout after login. Admin-only sections are hidden for waiters
/// (the backend enforces the same rules).
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

class _HomeShellState extends State<HomeShell> {
  int _index = 0;
  late final _menuApi = MenuApi(widget.session.api);
  late final _orderApi = OrderApi(widget.session.api);
  late final _tablesApi = TablesApi(widget.session.api);

  List<_Section> _sections(Me me) => me.isStation ? _stationSections() : [
        _Section('New order', Icons.add_shopping_cart,
            () => OrderScreen(orderApi: _orderApi, menuApi: _menuApi, tablesApi: _tablesApi)),
        _Section('History', Icons.receipt_long,
            () => OrderHistoryScreen(
                  orderApi: _orderApi,
                  isAdmin: me.isAdmin,
                  exportApi: me.isAdmin ? ExportApi(widget.session.api) : null,
                )),
        _Section('Menu', Icons.restaurant_menu, () => MenuScreen(menuApi: _menuApi)),
        if (me.isAdmin)
          _Section('Menu setup', Icons.edit_note,
              () => MenuAdminScreen(api: MenuAdminApi(widget.session.api))),
        if (me.isAdmin)
          _Section('Tables', Icons.table_restaurant, () => TablesScreen(api: _tablesApi)),
        if (me.isAdmin)
          _Section('Printers', Icons.print,
              () => PrintersScreen(api: PrintersApi(widget.session.api))),
        if (me.isAdmin)
          _Section('Staff', Icons.badge,
              () => StaffScreen(api: StaffApi(widget.session.api), me: me)),
      ];

  /// A station sees only the station view (spec staff-access, station permissions).
  List<_Section> _stationSections() => [
        _Section('Station', Icons.print,
            () => StationScreen(api: StationApi(widget.session.api), devices: widget.stationDevices)),
      ];

  @override
  Widget build(BuildContext context) {
    final me = widget.session.me!;
    final sections = _sections(me);
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
