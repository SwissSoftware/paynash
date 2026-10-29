import 'dart:typed_data';

import 'file_saver_stub.dart' if (dart.library.js_interop) 'file_saver_web.dart' as platform;

/// Saves a downloaded file on the device. On the web this is the browser's
/// download; the mobile apps get their own implementation later (design D1).
abstract class FileSaver {
  /// The saver for this platform. Tests replace it.
  static FileSaver instance = platform.create();

  Future<void> save(String filename, String mimeType, Uint8List bytes);
}
