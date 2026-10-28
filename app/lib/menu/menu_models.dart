enum ItemType { food, drink }

/// An item as offered to waiters (active items only).
class MenuItemData {
  MenuItemData({
    required this.id,
    required this.name,
    required this.type,
    required this.price,
    this.description,
    this.imageUrl,
  });

  factory MenuItemData.fromJson(Map<String, dynamic> json) => MenuItemData(
        id: json['id'] as int,
        name: json['name'] as String,
        type: ItemType.values.byName(json['type'] as String),
        price: json['price'] as String,
        description: json['description'] as String?,
        imageUrl: json['imageUrl'] as String?,
      );

  final int id;
  final String name;
  final ItemType type;

  /// CHF as delivered by the backend, e.g. "4.50" (no floating point).
  final String price;
  final String? description;
  final String? imageUrl;
}

class MenuCategoryData {
  MenuCategoryData({required this.id, required this.name, required this.items});

  factory MenuCategoryData.fromJson(Map<String, dynamic> json) => MenuCategoryData(
        id: json['id'] as int,
        name: json['name'] as String,
        items: [
          for (final item in json['items'] as List)
            MenuItemData.fromJson(item as Map<String, dynamic>),
        ],
      );

  final int id;
  final String name;
  final List<MenuItemData> items;
}
