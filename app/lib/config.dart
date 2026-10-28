/// Backend base URL. Override at build/run time with
/// `--dart-define=API_BASE_URL=https://pos.local`.
const String apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8081',
);
