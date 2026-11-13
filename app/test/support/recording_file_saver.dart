import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/export/file_saver.dart';

/// Records downloads instead of saving them.
class RecordingFileSaver implements FileSaver {
  final saved = <({String name, String mimeType, Uint8List bytes})>[];

  @override
  Future<void> save(String filename, String mimeType, Uint8List bytes) async =>
      saved.add((name: filename, mimeType: mimeType, bytes: bytes));

  /// Installs a recorder as [FileSaver.instance] for the current test.
  static RecordingFileSaver install() {
    final saver = RecordingFileSaver();
    final previous = FileSaver.instance;
    FileSaver.instance = saver;
    addTearDown(() => FileSaver.instance = previous);
    return saver;
  }
}
