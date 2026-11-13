import '../api/api_client.dart';
import '../export/file_saver.dart';
import 'customer_models.dart';

class CustomerApi {
  CustomerApi(this.api);

  final ApiClient api;

  /// Active customers matching [query] (name, number, phone, or email), at
  /// most 50. Only admins may include inactive ones.
  Future<List<CustomerData>> search(String query, {bool includeInactive = false}) async {
    final params = {
      if (query.trim().isNotEmpty) 'q': query.trim(),
      if (includeInactive) 'includeInactive': 'true',
    };
    final qs = params.isEmpty ? '' : '?${Uri(queryParameters: params).query}';
    return [
      for (final c in await api.get('/customers$qs') as List)
        CustomerData.fromJson(c as Map<String, dynamic>),
    ];
  }

  /// Customers with the same name, phone, or email; only a warning.
  Future<List<CustomerData>> duplicates(CustomerInput input, {int? exceptId}) async {
    final params = {
      if (input.firstName.trim().isNotEmpty) 'firstName': input.firstName.trim(),
      if (input.lastName.trim().isNotEmpty) 'lastName': input.lastName.trim(),
      if (input.phone.trim().isNotEmpty) 'phone': input.phone.trim(),
      if (input.email.trim().isNotEmpty) 'email': input.email.trim(),
      'exceptId': ?exceptId?.toString(),
    };
    return [
      for (final c in await api.get('/customers/duplicates?${Uri(queryParameters: params).query}')
          as List)
        CustomerData.fromJson(c as Map<String, dynamic>),
    ];
  }

  Future<CustomerDetail> detail(int id) async =>
      CustomerDetail.fromJson(await api.get('/customers/$id') as Map<String, dynamic>);

  Future<CustomerData> create(CustomerInput input) async =>
      CustomerData.fromJson(await api.post('/customers', input.toJson()) as Map<String, dynamic>);

  Future<CustomerData> update(int id, CustomerInput input) async => CustomerData.fromJson(
    await api.put('/customers/$id', input.toJson()) as Map<String, dynamic>,
  );

  Future<CustomerData> setActive(int id, bool active) async => CustomerData.fromJson(
    await api.post('/admin/customers/$id/${active ? 'reactivate' : 'deactivate'}')
        as Map<String, dynamic>,
  );

  /// "deleted" without orders, "anonymized" with orders.
  Future<String> delete(int id) async =>
      (await api.delete('/admin/customers/$id') as Map<String, dynamic>)['result'] as String;

  /// Downloads everything stored about the customer (access request).
  Future<String> export(CustomerData customer) async {
    final file = await api.getBytes('/admin/customers/${customer.id}/export');
    final name = file.filename ?? 'Kunde-${customer.number}.json';
    await FileSaver.instance.save(name, 'application/json', file.bytes);
    return name;
  }
}
