import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/admin/menu_admin_api.dart';
import 'package:paynash_pos/admin/menu_admin_screen.dart';
import 'package:paynash_pos/api/api_client.dart';
import 'package:paynash_pos/auth/token_store.dart';

import 'support/fake_backend.dart';
import 'support/test_image.dart';

Future<FakeBackend> pumpAdmin(WidgetTester tester, void Function(FakeBackend) seed,
    {ImagePicker? picker}) async {
  tester.view.physicalSize = const Size(1400, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final backend = FakeBackend()..addStaff('boss', 'admin', '1111');
  backend.sessions['t'] = backend.staff.first;
  seed(backend);
  final api = MenuAdminApi(ApiClient(
      baseUrl: 'http://backend',
      tokenStore: InMemoryTokenStore()..token = 't',
      httpClient: backend.client));
  await tester.pumpWidget(MaterialApp(
      home: Scaffold(
          body: MenuAdminScreen(api: api, pickImage: picker ?? () async => null))));
  await tester.pumpAndSettle();
  return backend;
}

Future<void> fillItem(WidgetTester tester,
    {required String name, String? type, required String price}) async {
  await tester.tap(find.byKey(const Key('item-add')));
  await tester.pumpAndSettle();
  await tester.enterText(find.byKey(const Key('item-name')), name);
  if (type != null) {
    await tester.tap(find.descendant(
        of: find.byKey(const Key('item-type')), matching: find.text(type)));
  }
  await tester.enterText(find.byKey(const Key('item-price')), price);
}

void main() {
  testWidgets('admin creates a category', (tester) async {
    final backend = await pumpAdmin(tester, (_) {});

    await tester.tap(find.byKey(const Key('category-add')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('category-name')), findsOneWidget, reason: 'dialog open');
    await tester.enterText(find.byKey(const Key('category-name')), 'Hot drinks');
    await tester.tap(find.byKey(const Key('category-save')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('category-name')), findsNothing, reason: 'dialog closed');

    expect(backend.categories.single['name'], 'Hot drinks');
    expect(find.text('Hot drinks'), findsOneWidget);
  });

  testWidgets('admin creates an item with an image, shown with preview and thumbnail',
      (tester) async {
    final backend = await pumpAdmin(tester, (b) => b.addCategory('Pizzas'),
        picker: () async => PickedImage(onePixelPng, 'pizza.png'));

    await fillItem(tester, name: 'Margherita', type: 'Food', price: '18.00');
    await tester.tap(find.byKey(const Key('image-choose')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('image-preview')), findsOneWidget);
    await tester.tap(find.byKey(const Key('item-save')));
    await tester.pumpAndSettle();

    final item = backend.items.single;
    expect(item['name'], 'Margherita');
    expect(item['type'], 'food');
    expect(item['price'], '18.00');
    expect(item['imageUrl'], '/images/upload-1.webp');
    final thumb = tester.widget<Image>(find.byKey(Key('thumb-${item['id']}')));
    expect((thumb.image as NetworkImage).url, 'http://backend/images/upload-1.webp');
  });

  testWidgets('missing type shows the validation error', (tester) async {
    final backend = await pumpAdmin(tester, (b) => b.addCategory('Pizzas'));

    await fillItem(tester, name: 'Margherita', price: '18.00');
    await tester.tap(find.byKey(const Key('item-save')));
    await tester.pumpAndSettle();

    expect(find.text('Type is required (food or drink)'), findsOneWidget);
    expect(backend.items, isEmpty);
  });

  testWidgets('price field accepts at most two decimals', (tester) async {
    await pumpAdmin(tester, (b) => b.addCategory('Pizzas'));
    await fillItem(tester, name: 'X', type: 'Food', price: '4.505');
    final field = tester.widget<TextField>(find.byKey(const Key('item-price')));
    expect(field.controller!.text, isNot('4.505'));
  });

  testWidgets('admin removes an image', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      id = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00',
          imageUrl: '/images/old.webp');
    });

    await tester.tap(find.byKey(Key('item-$id')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('image-remove')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('image-placeholder')), findsOneWidget);
    await tester.tap(find.byKey(const Key('item-save')));
    await tester.pumpAndSettle();

    expect(backend.items.single['imageUrl'], isNull);
  });

  testWidgets('admin deactivates an item', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      id = b.addItem(b.addCategory('Desserts'), 'Tiramisu', 'food', '9.00');
    });

    await tester.tap(find.byKey(Key('item-$id')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('item-active')));
    await tester.tap(find.byKey(const Key('item-save')));
    await tester.pumpAndSettle();

    expect(backend.items.single['active'], false);
    expect(find.text('CHF 9.00 · inactive'), findsOneWidget);
  });

  testWidgets('deleting a non-empty category shows the blocking items', (tester) async {
    late int cat;
    final backend = await pumpAdmin(tester, (b) {
      cat = b.addCategory('Pizzas');
      b.addItem(cat, 'Margherita', 'food', '18.00');
    });

    await tester.tap(find.descendant(
        of: find.byKey(Key('category-$cat')), matching: find.byType(PopupMenuButton<String>)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();

    expect(find.text('The category still contains items: Margherita'), findsOneWidget);
    expect(backend.categories, hasLength(1));
  });
}
