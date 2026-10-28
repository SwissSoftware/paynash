import 'package:flutter/material.dart';

import '../api/api_client.dart';
import 'menu_api.dart';
import 'menu_browser.dart';
import 'menu_models.dart';

/// The active menu as waiters see it.
class MenuScreen extends StatefulWidget {
  const MenuScreen({super.key, required this.menuApi});

  final MenuApi menuApi;

  @override
  State<MenuScreen> createState() => _MenuScreenState();
}

class _MenuScreenState extends State<MenuScreen> {
  List<MenuCategoryData>? _categories;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final categories = await widget.menuApi.load();
      if (mounted) {
        setState(() {
          _categories = categories;
          _error = null;
        });
      }
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(_error!),
          TextButton(onPressed: _load, child: const Text('Retry')),
        ]),
      );
    }
    final categories = _categories;
    if (categories == null) return const Center(child: CircularProgressIndicator());
    return RefreshIndicator(
      onRefresh: _load,
      child: MenuBrowser(categories: categories, imageUrl: widget.menuApi.imageUrl),
    );
  }
}
