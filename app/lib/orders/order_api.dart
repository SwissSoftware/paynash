import '../api/api_client.dart';
import 'order_models.dart';

/// Sending orders and reading the order history. Sending, cancelling, and
/// voiding carry an idempotency key, so a retried request has one effect.
class OrderApi {
  OrderApi(this.api);

  final ApiClient api;

  final _etags = <int, String>{};
  final _cached = <int, OrderData>{};

  /// Orders sent on [day] (a local date), or today if null. Newest first.
  Future<List<OrderSummary>> history([DateTime? day]) async {
    final query = day == null
        ? ''
        : '?date=${day.year.toString().padLeft(4, '0')}-${day.month.toString().padLeft(2, '0')}'
              '-${day.day.toString().padLeft(2, '0')}';
    return [
      for (final json in await api.get('/orders$query') as List)
        OrderSummary.fromJson(json as Map<String, dynamic>),
    ];
  }

  /// Polls one order. Unchanged orders cost only a 304 response (ETag).
  Future<OrderData> load(int id) async {
    final result = await api.getIfChanged('/orders/$id', _cached[id] == null ? null : _etags[id]);
    if (result == null) return _cached[id]!;
    if (result.etag != null) _etags[id] = result.etag!;
    return _remember(result.body);
  }

  /// Sends the whole order. Pass the same [idempotencyKey] when retrying the
  /// same draft, so an order that reached the server is not sent twice.
  Future<OrderData> send(String tableLabel, List<DraftLine> lines, String idempotencyKey) async =>
      _remember(
        await api.post('/orders', {
          'tableLabel': tableLabel,
          'lines': [for (final l in lines) l.toJson()],
        }, idempotencyKey),
      );

  Future<OrderData> cancelLine(int orderId, int lineId, String reason) async => _remember(
    await api.post('/admin/orders/$orderId/lines/$lineId/cancel', {
      'reason': reason,
    }, newIdempotencyKey()),
  );

  Future<OrderData> voidOrder(OrderData order, String reason) async => _remember(
    await api.post('/admin/orders/${order.id}/void', {
      'reason': reason,
      'version': order.version,
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
