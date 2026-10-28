import 'package:flutter/material.dart';

import '../admin/menu_admin_api.dart';
import '../admin/menu_admin_screen.dart';
import '../admin/staff_api.dart';
import '../admin/staff_screen.dart';
import '../auth/session.dart';
import '../menu/menu_api.dart';
import '../menu/menu_screen.dart';
import '../orders/order_api.dart';
import '../orders/orders_screen.dart';

/// Main layout after login. Admin-only sections are hidden for waiters
/// (the backend enforces the same rules).
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.session});

  final SessionController session;

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

  List<_Section> _sections(Me me) => [
        _Section('Orders', Icons.receipt_long,
            () => OrdersScreen(orderApi: _orderApi, menuApi: _menuApi)),
        _Section('Menu', Icons.restaurant_menu, () => MenuScreen(menuApi: _menuApi)),
        if (me.isAdmin)
          _Section('Menu setup', Icons.edit_note,
              () => MenuAdminScreen(api: MenuAdminApi(widget.session.api))),
        if (me.isAdmin)
          _Section('Staff', Icons.badge,
              () => StaffScreen(api: StaffApi(widget.session.api), me: me)),
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
      bottomNavigationBar: !wide && sections.length > 1
          ? NavigationBar(
              selectedIndex: index,
              onDestinationSelected: (i) => setState(() => _index = i),
              destinations: [
                for (final s in sections)
                  NavigationDestination(icon: Icon(s.icon), label: s.label),
              ],
            )
          : null,
    );
  }
}
