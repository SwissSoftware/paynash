import 'dart:math';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

/// Plays the station's alert sound (spec print-station, "Station alerts").
abstract class AlertSound {
  /// Browsers only play sound after the user touched the page, so the station
  /// asks for one tap after login and calls this from it.
  Future<void> arm();

  Future<void> beep();
}

/// Keeps the display on while the station screen is open (spec print-station,
/// "Station stays usable for the whole event").
abstract class KeepAwake {
  Future<void> enable();

  Future<void> disable();
}

/// The station's device features. Tests replace them with fakes.
class StationDevices {
  const StationDevices({required this.sound, required this.keepAwake});

  factory StationDevices.platform() =>
      StationDevices(sound: _PlayerAlertSound(), keepAwake: _WakelockKeepAwake());

  final AlertSound sound;
  final KeepAwake keepAwake;
}

class _PlayerAlertSound implements AlertSound {
  // Created on first use, so devices that never open the station have none.
  late final _player = AudioPlayer();
  final _wav = beepWav();

  @override
  Future<void> arm() => beep();

  @override
  Future<void> beep() async {
    try {
      await _player.stop();
      await _player.play(BytesSource(_wav, mimeType: 'audio/wav'));
    } catch (_) {
      // A missing sound must never break the screen; the red banner stays.
    }
  }
}

class _WakelockKeepAwake implements KeepAwake {
  @override
  Future<void> enable() async {
    try {
      await WakelockPlus.enable();
    } catch (_) {
      // Not supported on this device; the setup guide covers screen timeouts.
    }
  }

  @override
  Future<void> disable() async {
    try {
      await WakelockPlus.disable();
    } catch (_) {}
  }
}

/// Two short high beeps as a 16-bit mono WAV, built in code so no audio file
/// has to be shipped.
Uint8List beepWav() {
  const rate = 22050;
  const tone = 0.15, gap = 0.08;
  final samples = <int>[];
  void beep(double freq) {
    final n = (rate * tone).round();
    for (var i = 0; i < n; i++) {
      // Short fade in and out avoids clicks.
      final fade = min(1.0, min(i, n - i) / (rate * 0.01));
      samples.add((sin(2 * pi * freq * i / rate) * 0.6 * fade * 32767).round());
    }
  }

  void silence(double seconds) => samples.addAll(List.filled((rate * seconds).round(), 0));
  beep(1200);
  silence(gap);
  beep(1600);
  silence(gap);

  final data = ByteData(44 + samples.length * 2);
  void ascii(int offset, String s) {
    for (var i = 0; i < s.length; i++) {
      data.setUint8(offset + i, s.codeUnitAt(i));
    }
  }

  ascii(0, 'RIFF');
  data.setUint32(4, 36 + samples.length * 2, Endian.little);
  ascii(8, 'WAVE');
  ascii(12, 'fmt ');
  data.setUint32(16, 16, Endian.little);
  data.setUint16(20, 1, Endian.little); // PCM
  data.setUint16(22, 1, Endian.little); // mono
  data.setUint32(24, rate, Endian.little);
  data.setUint32(28, rate * 2, Endian.little);
  data.setUint16(32, 2, Endian.little);
  data.setUint16(34, 16, Endian.little);
  ascii(36, 'data');
  data.setUint32(40, samples.length * 2, Endian.little);
  for (var i = 0; i < samples.length; i++) {
    data.setInt16(44 + i * 2, samples[i], Endian.little);
  }
  return data.buffer.asUint8List();
}
