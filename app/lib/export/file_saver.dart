import 'dart:typed_data';

import 'file_saver_io.dart' if (dart.library.js_interop) 'file_saver_web.dart' as platform;

/// Saves a downloaded file on the device: the browser's download on the web,
/// the share sheet on Android and iOS.
abstract class FileSaver {
  /// The saver for this platform. Tests replace it.
  static FileSaver instance = platform.create();

  Future<void> save(String filename, String mimeType, Uint8List bytes);
}
