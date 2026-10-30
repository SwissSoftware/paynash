import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

import 'file_saver.dart';

FileSaver create() => _BrowserFileSaver();

/// Hands the bytes to the browser as a download via a temporary object URL.
class _BrowserFileSaver implements FileSaver {
  @override
  Future<void> save(String filename, String mimeType, Uint8List bytes) async {
    final blob = web.Blob([bytes.toJS].toJS, web.BlobPropertyBag(type: mimeType));
    final url = web.URL.createObjectURL(blob);
    try {
      (web.HTMLAnchorElement()
            ..href = url
            ..download = filename)
          .click();
    } finally {
      web.URL.revokeObjectURL(url);
    }
  }
}
