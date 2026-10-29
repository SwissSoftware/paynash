import 'dart:typed_data';

import 'file_saver.dart';

FileSaver create() => _UnsupportedFileSaver();

class _UnsupportedFileSaver implements FileSaver {
  @override
  Future<void> save(String filename, String mimeType, Uint8List bytes) =>
      throw UnsupportedError('Downloads are only supported in the web app so far');
}
