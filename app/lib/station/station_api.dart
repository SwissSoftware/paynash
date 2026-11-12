import '../api/api_client.dart';
import '../orders/order_models.dart';
import 'station_models.dart';

/// The print station's endpoints (`/station/**`). Changing requests carry an
/// idempotency key, so a retried request has one effect.
class StationApi {
  StationApi(this.api);

  final ApiClient api;

  final _ticketEtags = <int, String>{};
  final _tickets = <int, List<StationTicket>>{};

  Future<List<StationPrinter>> printers() async => [
    for (final json in await api.get('/station/printers') as List)
      StationPrinter.fromJson(json as Map<String, dynamic>),
  ];

  /// Polls a printer's tickets. An unchanged list costs only a 304 (ETag).
  Future<List<StationTicket>> tickets(int printerId) async {
    final cached = _tickets[printerId];
    final result = await api.getIfChanged(
      '/station/tickets?printerId=$printerId',
      cached == null ? null : _ticketEtags[printerId],
    );
    if (result == null) return cached!;
    final list = [
      for (final json in result.body as List) StationTicket.fromJson(json as Map<String, dynamic>),
    ];
    _tickets[printerId] = list;
    if (result.etag != null) _ticketEtags[printerId] = result.etag!;
    return list;
  }

  /// "printed": the ticket was in the pile. "reprint": print it again.
  Future<void> resolve(int ticketId, String resolution) async {
    await api.post('/station/tickets/$ticketId/resolve', {
      'resolution': resolution,
    }, newIdempotencyKey());
    _forgetTickets();
  }

  Future<void> reprint(int ticketId) async {
    await api.post('/station/tickets/$ticketId/reprint', null, newIdempotencyKey());
    _forgetTickets();
  }

  Future<OrderData> order(int orderId) async =>
      OrderData.fromJson(await api.get('/station/orders/$orderId') as Map<String, dynamic>);

  /// Throws [ApiError] with a field error on `pin` when username or PIN are wrong.
  Future<OrderData> cancelLine(
    int orderId,
    int lineId, {
    required String username,
    required String pin,
    required String reason,
  }) async {
    final json = await api.post('/station/orders/$orderId/lines/$lineId/cancel', {
      'username': username,
      'pin': pin,
      'reason': reason,
    }, newIdempotencyKey());
    _forgetTickets();
    return OrderData.fromJson(json as Map<String, dynamic>);
  }

  Future<OrderData> voidOrder(
    OrderData order, {
    required String username,
    required String pin,
    required String reason,
  }) async {
    final json = await api.post('/station/orders/${order.id}/void', {
      'username': username,
      'pin': pin,
      'reason': reason,
      'version': order.version,
    }, newIdempotencyKey());
    _forgetTickets();
    return OrderData.fromJson(json as Map<String, dynamic>);
  }

  /// After a change, the next poll fetches the full lists again.
  void _forgetTickets() => _ticketEtags.clear();
}
