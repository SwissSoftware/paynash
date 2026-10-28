import 'package:file_picker/file_picker.dart';

import '../api/api_client.dart';
import '../menu/menu_models.dart';

class AdminCategory {
  AdminCategory({required this.id, required this.name});

  factory AdminCategory.fromJson(Map<String, dynamic> json) =>
      AdminCategory(id: json['id'] as int, name: json['name'] as String);

  final int id;
  final String name;
}

class AdminItem {
  AdminItem({
    required this.id,
    required this.categoryId,
    required this.name,
    required this.type,
    required this.price,
    required this.active,
    this.description,
    this.imageUrl,
  });

  factory AdminItem.fromJson(Map<String, dynamic> json) => AdminItem(
        id: json['id'] as int,
        categoryId: json['categoryId'] as int,
        name: json['name'] as String,
        type: ItemType.values.byName(json['type'] as String),
        price: json['price'] as String,
        active: json['active'] as bool,
        description: json['description'] as String?,
        imageUrl: json['imageUrl'] as String?,
      );

  final int id;
  final int categoryId;
  final String name;
  final ItemType type;
  final String price;
  final bool active;
  final String? description;
  final String? imageUrl;
}

class ItemInput {
  ItemInput({
    required this.categoryId,
    required this.name,
    required this.type,
    required this.price,
    required this.description,
    required this.active,
  });

  final int categoryId;
  final String name;
  final ItemType? type;
  final String price;
  final String description;
  final bool active;

  Map<String, dynamic> toJson() => {
        'categoryId': categoryId,
        'name': name,
        'type': type?.name,
        'price': price,
        'description': description,
        'active': active,
      };
}

/// An image chosen by the admin, not yet uploaded.
class PickedImage {
  PickedImage(this.bytes, this.filename);
  final List<int> bytes;
  final String filename;
}

typedef ImagePicker = Future<PickedImage?> Function();

Future<PickedImage?> pickImageFromDevice() async {
  final file = await FilePicker.pickFile(type: FileType.image);
  if (file == null) return null;
  return PickedImage(await file.readAsBytes(), file.name);
}

class MenuAdminApi {
  MenuAdminApi(this.api);

  final ApiClient api;

  Future<List<AdminCategory>> categories() async => [
        for (final c in await api.get('/admin/categories') as List)
          AdminCategory.fromJson(c as Map<String, dynamic>),
      ];

  Future<void> createCategory(String name) => api.post('/admin/categories', {'name': name});

  Future<void> renameCategory(int id, String name) =>
      api.put('/admin/categories/$id', {'name': name});

  Future<void> deleteCategory(int id) => api.delete('/admin/categories/$id');

  Future<void> reorderCategories(List<int> ids) =>
      api.put('/admin/categories/order', {'ids': ids});

  Future<List<AdminItem>> items() async => [
        for (final i in await api.get('/admin/items') as List)
          AdminItem.fromJson(i as Map<String, dynamic>),
      ];

  Future<AdminItem> createItem(ItemInput input) async => AdminItem.fromJson(
      await api.post('/admin/items', input.toJson()) as Map<String, dynamic>);

  Future<AdminItem> updateItem(int id, ItemInput input) async => AdminItem.fromJson(
      await api.put('/admin/items/$id', input.toJson()) as Map<String, dynamic>);

  Future<void> reorderItems(int categoryId, List<int> ids) =>
      api.put('/admin/categories/$categoryId/items/order', {'ids': ids});

  Future<void> uploadImage(int itemId, PickedImage image) =>
      api.upload('/admin/items/$itemId/image', image.bytes, image.filename);

  Future<void> removeImage(int itemId) => api.delete('/admin/items/$itemId/image');

  String imageUrl(String path) => api.resolve(path);
}
