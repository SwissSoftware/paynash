import '../api/api_client.dart';
import '../auth/session.dart';

class StaffMember {
  StaffMember({
    required this.id,
    required this.username,
    required this.displayName,
    required this.role,
    required this.active,
  });

  factory StaffMember.fromJson(Map<String, dynamic> json) => StaffMember(
        id: json['id'] as int,
        username: json['username'] as String,
        displayName: json['displayName'] as String,
        role: Role.values.byName(json['role'] as String),
        active: json['active'] as bool,
      );

  final int id;
  final String username;
  final String displayName;
  final Role role;
  final bool active;
}

class StaffApi {
  StaffApi(this._api);

  final ApiClient _api;

  Future<List<StaffMember>> list() async => [
        for (final json in await _api.get('/admin/staff') as List)
          StaffMember.fromJson(json as Map<String, dynamic>),
      ];

  Future<void> create(
          {required String username,
          required String displayName,
          required Role role,
          required String pin}) =>
      _api.post('/admin/staff', {
        'username': username,
        'displayName': displayName,
        'role': role.name,
        'pin': pin,
      });

  Future<void> update(int id,
          {required String displayName, required Role role, required bool active}) =>
      _api.put('/admin/staff/$id',
          {'displayName': displayName, 'role': role.name, 'active': active});

  Future<void> resetPin(int id, String pin) =>
      _api.post('/admin/staff/$id/pin', {'pin': pin});
}
