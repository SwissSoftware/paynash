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

  /// Only possible while no order exists; otherwise the server answers 409.
  Future<AppSettings> changeMode(VenueMode mode) async => AppSettings.fromJson(
    await api.put('/admin/settings/mode', {'mode': mode.name}) as Map<String, dynamic>,
  );
}
