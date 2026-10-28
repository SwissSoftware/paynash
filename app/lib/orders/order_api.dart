import '../api/api_client.dart';
import 'order_models.dart';

/// Order taking. Adding, sending, and cancelling carry an idempotency key, so
/// a retried request cannot add a line, round, or cancellation twice.
class OrderApi {
  OrderApi(this.api);

  final ApiClient api;

  final _etags = <int, String>{};
  final _cached = <int, OrderData>{};

  Future<List<OpenOrderSummary>> openOrders() async => [
    for (final json in await api.get('/orders') as List)
      OpenOrderSummary.fromJson(json as Map<String, dynamic>),
  ];

  /// Opens the table's order, or returns the one that is already open.
  Future<OrderData> open(String tableLabel) async =>
      _remember(await api.post('/orders', {'tableLabel': tableLabel}));

  /// Polls one order. Unchanged orders cost only a 304 response (ETag).
  Future<OrderData> load(int id) async {
    final result = await api.getIfChanged('/orders/$id', _cached[id] == null ? null : _etags[id]);
    if (result == null) return _cached[id]!;
    if (result.etag != null) _etags[id] = result.etag!;
    return _remember(result.body);
  }

  Future<OrderData> addLine(int orderId, int menuItemId, {int qty = 1, String? note}) async =>
      _remember(
        await api.post('/orders/$orderId/lines', {
          'menuItemId': menuItemId,
          'qty': qty,
          'note': note,
        }, newIdempotencyKey()),
      );

  Future<OrderData> updateLine(
    int orderId,
    OrderLineData line, {
    required int qty,
    String? note,
  }) async => _remember(
    await api.put('/orders/$orderId/lines/${line.id}', {
      'qty': qty,
      'note': note,
      'version': line.version,
    }),
  );

  Future<OrderData> removeLine(int orderId, int lineId) async =>
      _remember(await api.delete('/orders/$orderId/lines/$lineId'));

  /// Returns the round number, or null if there was nothing to send.
  Future<({OrderData order, int? round})> send(int orderId) async {
    final json =
        await api.post('/orders/$orderId/send', null, newIdempotencyKey()) as Map<String, dynamic>;
    final round = json['round'] as Map<String, dynamic>?;
    return (order: _remember(json['order']), round: round?['number'] as int?);
  }

  Future<OrderData> cancelLine(int orderId, int lineId, String reason) async => _remember(
    await api.post('/orders/$orderId/lines/$lineId/cancel', {
      'reason': reason,
    }, newIdempotencyKey()),
  );

  OrderData _remember(dynamic json) {
    final order = OrderData.fromJson(json as Map<String, dynamic>);
    _cached[order.id] = order;
    // The ETag of the previous version no longer matches; the next poll
    // fetches the full order once and stores its new ETag.
    _etags.remove(order.id);
    return order;
  }
}
