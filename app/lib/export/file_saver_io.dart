import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import 'file_saver.dart';

FileSaver create() => _ShareSheetFileSaver();

/// Android and iOS: writes the file to the app's temporary folder and opens
/// the share sheet, so staff can save, print, or send it (add-salon-mode
/// design D11).
class _ShareSheetFileSaver implements FileSaver {
  @override
  Future<void> save(String filename, String mimeType, Uint8List bytes) async {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/$filename');
    await file.writeAsBytes(bytes, flush: true);
    await SharePlus.instance.share(
      ShareParams(files: [XFile(file.path, mimeType: mimeType, name: filename)], title: filename),
    );
  }
}
