import 'package:flutter/material.dart';

import 'item_visuals.dart';
import 'menu_models.dart';

/// Image tiles for the active menu with a category filter. Order taking
/// (task 4.9) reuses this through [onItemTap].
class MenuBrowser extends StatefulWidget {
  const MenuBrowser({
    super.key,
    required this.categories,
    required this.imageUrl,
    this.onItemTap,
  });

  final List<MenuCategoryData> categories;
  final String Function(String path) imageUrl;
  final void Function(MenuItemData item)? onItemTap;

  @override
  State<MenuBrowser> createState() => _MenuBrowserState();
}

class _MenuBrowserState extends State<MenuBrowser> {
  int? _categoryId;

  @override
  Widget build(BuildContext context) {
    final categories = widget.categories;
    if (categories.isEmpty) {
      return const Center(child: Text('The menu is empty.'));
    }
    final selected = categories.where((c) => c.id == _categoryId).toList();
    final shown = selected.isEmpty ? categories : selected;
    final items = [for (final c in shown) ...c.items];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
          child: Row(
            children: [
              _chip('All', null),
              for (final c in categories) _chip(c.name, c.id),
            ],
          ),
        ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(12),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 180,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.8,
            ),
            itemCount: items.length,
            itemBuilder: (context, i) => MenuTile(
              item: items[i],
              imageUrl: widget.imageUrl,
              onTap: widget.onItemTap == null ? null : () => widget.onItemTap!(items[i]),
            ),
          ),
        ),
      ],
    );
  }

  Widget _chip(String label, int? id) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChoiceChip(
          key: Key('category-chip-${id ?? 'all'}'),
          label: Text(label),
          selected: _categoryId == id,
          onSelected: (_) => setState(() => _categoryId = id),
        ),
      );
}

class MenuTile extends StatelessWidget {
  const MenuTile({super.key, required this.item, required this.imageUrl, this.onTap});

  final MenuItemData item;
  final String Function(String path) imageUrl;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      key: Key('menu-tile-${item.id}'),
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: item.type.color, width: 2),
      ),
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  item.imageUrl == null
                      ? ItemPlaceholder(item: item)
                      : Image.network(
                          // Image URLs are content-addressed and cached by
                          // the browser and Flutter's image cache.
                          imageUrl(item.imageUrl!),
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => ItemPlaceholder(item: item),
                        ),
                  Positioned(top: 6, right: 6, child: ItemTypeBadge(item.type)),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item.name,
                      style: text.titleSmall, maxLines: 2, overflow: TextOverflow.ellipsis),
                  Text('CHF ${item.price}', style: text.bodySmall),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Shown when an item has no image: the name with a food or drink icon.
class ItemPlaceholder extends StatelessWidget {
  const ItemPlaceholder({super.key, required this.item});

  final MenuItemData item;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: Key('placeholder-${item.id}'),
      color: item.type.color.withValues(alpha: 0.12),
      padding: const EdgeInsets.all(8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(item.type.icon, size: 40, color: item.type.color),
          const SizedBox(height: 4),
          Text(item.name,
              textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis),
        ],
      ),
    );
  }
}
