import 'package:flutter/material.dart';

import 'menu_models.dart';

/// Colours and icons that tell food, drinks, and services apart at a glance.
extension ItemTypeVisuals on ItemType {
  IconData get icon => switch (this) {
        ItemType.food => Icons.restaurant,
        ItemType.drink => Icons.local_bar,
        ItemType.service => Icons.spa,
      };

  Color get color => switch (this) {
        ItemType.food => const Color(0xFFE65100),
        ItemType.drink => const Color(0xFF1565C0),
        ItemType.service => const Color(0xFF6A1B9A),
      };

  String get label => switch (this) {
        ItemType.food => 'Food',
        ItemType.drink => 'Drink',
        ItemType.service => 'Service',
      };
}

/// Small coloured badge with the food/drink icon.
class ItemTypeBadge extends StatelessWidget {
  const ItemTypeBadge(this.type, {super.key, this.size = 16});

  final ItemType type;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: type.label,
      child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(color: type.color, shape: BoxShape.circle),
        child: Icon(type.icon, size: size, color: Colors.white),
      ),
    );
  }
}
