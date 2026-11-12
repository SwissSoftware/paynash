import 'station_models.dart';

/// A ticket that waited longer than this raises an alert.
const waitingAlertSeconds = 30;

/// Without an answer from the server for longer than this, the station alerts.
const connectionAlertAfter = Duration(seconds: 10);

/// Why the station alerts right now (spec print-station, "Station alerts").
/// Empty when everything is fine; the alert then ends by itself.
List<String> alertCauses(List<StationPrinter> printers, {Duration? noConnectionFor}) {
  final causes = <String>[];
  if (noConnectionFor != null && noConnectionFor > connectionAlertAfter) {
    causes.add('No connection to the server');
  }
  for (final p in printers) {
    if (p.hasProblem) {
      causes.add(
        '${p.name}: ${p.status}'
        '${p.waiting > 0 ? ', ${_tickets(p.waiting)} waiting' : ''}',
      );
    } else if ((p.oldestWaitingSeconds ?? 0) > waitingAlertSeconds) {
      causes.add('${p.name}: ${_tickets(p.waiting)} waiting for ${p.oldestWaitingSeconds} s');
    }
    if (p.unclear > 0) {
      causes.add(
        '${p.name}: ${p.unclear} unclear ${p.unclear == 1 ? 'ticket' : 'tickets'}, check the pile',
      );
    }
  }
  return causes;
}

String _tickets(int n) => n == 1 ? '1 ticket' : '$n tickets';
