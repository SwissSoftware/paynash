import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import 'settings_api.dart';
import 'settings_models.dart';

/// Holds the installation settings for the logged-in app. Loaded after login
/// and whenever the app comes back to the foreground, so a mode change by an
/// admin reaches every device (add-salon-mode design D11).
class SettingsController extends ChangeNotifier {
  SettingsController(this.api);

  final SettingsApi api;

  AppSettings? settings;
  String? error;

  Future<void> load() async {
    try {
      settings = await api.load();
      error = null;
    } on ApiError catch (e) {
      // Keep the last known settings; only show the error if there are none.
      error = e.message;
    }
    notifyListeners();
  }

  /// After the settings screen saved new values.
  void update(AppSettings value) {
    settings = value;
    error = null;
    notifyListeners();
  }
}
