import '../api/api_client.dart';
import 'menu_models.dart';

/// Loads the waiter menu and keeps the last version, so an unchanged menu
/// costs only a 304 response (ETag).
class MenuApi {
  MenuApi(this.api);

  final ApiClient api;

  String? _etag;
  List<MenuCategoryData>? _cached;

  Future<List<MenuCategoryData>> load() async {
    final result = await api.getIfChanged('/menu', _cached == null ? null : _etag);
    if (result == null) return _cached!;
    _etag = result.etag;
    return _cached = [
      for (final c in (result.body as Map<String, dynamic>)['categories'] as List)
        MenuCategoryData.fromJson(c as Map<String, dynamic>),
    ];
  }

  String imageUrl(String path) => api.resolve(path);
}
