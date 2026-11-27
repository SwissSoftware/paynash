import '../api/api_client.dart';
import 'settings_models.dart';

class SettingsApi {
  SettingsApi(this.api);

  final ApiClient api;

  Future<AppSettings> load() async =>
      AppSettings.fromJson(await api.get('/settings') as Map<String, dynamic>);

  /// Saves the business details and the VAT setting (admin).
  Future<AppSettings> save(Map<String, dynamic> details) async =>
      AppSettings.fromJson(await api.put('/admin/settings', details) as Map<String, dynamic>);

  /// Switches features on and off (admin). The server rejects broken
  /// dependencies with 400; switching off keeps the data.
  Future<AppSettings> saveFeatures(Set<Feature> features) async => AppSettings.fromJson(
    await api.put('/admin/settings/features', {
      for (final f in Feature.values) f.key: features.contains(f),
    }) as Map<String, dynamic>,
  );

  /// Renames the three terms (admin).
  Future<AppSettings> saveTerms(Terms terms) async => AppSettings.fromJson(
    await api.put('/admin/settings/terms', terms.toJson()) as Map<String, dynamic>,
  );

  /// Sets features and terms from a template (admin).
  Future<AppSettings> applyTemplate(BusinessTemplate template) async => AppSettings.fromJson(
    await api.post('/admin/settings/template', {'template': template.name}) as Map<String, dynamic>,
  );
}
