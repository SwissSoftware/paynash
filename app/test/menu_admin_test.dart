import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/admin/menu_admin_api.dart';
import 'package:paynash_pos/admin/menu_admin_screen.dart';
import 'package:paynash_pos/api/api_client.dart';
import 'package:paynash_pos/auth/token_store.dart';
import 'package:paynash_pos/settings/settings_models.dart';

import 'support/fake_backend.dart';
import 'support/test_image.dart';

Future<FakeBackend> pumpAdmin(WidgetTester tester, void Function(FakeBackend) seed,
    {ImagePicker? picker, bool salon = false, bool vat = false}) async {
  tester.view.physicalSize = const Size(1400, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final backend = FakeBackend()..addStaff('boss', 'admin', '1111');
  backend.sessions['t'] = backend.staff.first;
  if (salon) backend.salon.useSalon(vat: vat);
  seed(backend);
  final api = MenuAdminApi(ApiClient(
      baseUrl: 'http://backend',
      tokenStore: InMemoryTokenStore()..token = 't',
      httpClient: backend.client));
  await tester.pumpWidget(MaterialApp(
      home: Scaffold(
          body: MenuAdminScreen(
              api: api,
              pickImage: picker ?? () async => null,
              terms: salon ? const Terms(waiter: 'Staff', items: 'Services', send: 'Confirm') : Terms.standard,
              vatEnabled: vat))));
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

    expect(find.text('Type is required (food, drink, or service)'), findsOneWidget);
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

  Future<void> deleteViaMenu(WidgetTester tester, int id) async {
    await tester.tap(find.byKey(Key('item-menu-$id')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
  }

  testWidgets('admin deletes an item that was never ordered', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      final cat = b.addCategory('Hot drinks');
      id = b.addItem(cat, 'Espreso', 'drink', '4.50');
      b.addItem(cat, 'Tea', 'drink', '4.00');
    });

    await deleteViaMenu(tester, id);
    expect(find.text('Delete Espreso?'), findsOneWidget);
    await tester.tap(find.byKey(const Key('item-delete-confirm')));
    await tester.pumpAndSettle();

    expect(backend.items.map((i) => i['name']), ['Tea']);
    expect(find.text('Espreso'), findsNothing);
  });

  testWidgets('cancelling the confirmation keeps the item', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      id = b.addItem(b.addCategory('Hot drinks'), 'Espresso', 'drink', '4.50');
    });

    await deleteViaMenu(tester, id);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(backend.items, hasLength(1));
    expect(find.text('Espresso'), findsOneWidget);
  });

  testWidgets('an ordered item cannot be deleted and can be deactivated instead', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      id = b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00');
      b.addSentOrder('12', b.staff.first, [(id, 1)]);
    });

    await deleteViaMenu(tester, id);
    await tester.tap(find.byKey(const Key('item-delete-confirm')));
    await tester.pumpAndSettle();

    expect(find.text('Margherita was already ordered and cannot be deleted. Deactivate it instead.'),
        findsOneWidget);
    await tester.tap(find.byKey(const Key('item-deactivate-instead')));
    await tester.pumpAndSettle();

    expect(backend.items.single['active'], false);
    expect(find.text('CHF 18.00 · inactive'), findsOneWidget);
  });

  testWidgets('an inactive ordered item only offers OK', (tester) async {
    late int id;
    final backend = await pumpAdmin(tester, (b) {
      id = b.addItem(b.addCategory('Pizzas'), 'Funghi', 'food', '20.00', active: false);
      b.addSentOrder('12', b.staff.first, [(id, 1)]);
    });

    await deleteViaMenu(tester, id);
    await tester.tap(find.byKey(const Key('item-delete-confirm')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('item-deactivate-instead')), findsNothing);
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
    expect(backend.items, hasLength(1));
  });

  group('salon services', () {
    testWidgets('admin creates a service with a duration; the type starts as service', (
      tester,
    ) async {
      final backend = await pumpAdmin(
        tester,
        (b) => b.addCategory('Face'),
        salon: true,
      );
      expect(find.text('Add service'), findsOneWidget);
      await tester.tap(find.byKey(const Key('item-add')));
      await tester.pumpAndSettle();
      // All types can be chosen; where items are called services, service is preselected.
      expect(find.byKey(const Key('item-type')), findsOneWidget);
      expect(
        find.byKey(const Key('item-vat-rate')),
        findsNothing,
        reason: 'VAT is off',
      );
      await tester.enterText(find.byKey(const Key('item-name')), 'Facial');
      await tester.enterText(find.byKey(const Key('item-price')), '120.00');
      await tester.enterText(find.byKey(const Key('item-duration')), '60');
      await tester.tap(find.byKey(const Key('item-save')));
      await tester.pumpAndSettle();

      final item = backend.items.single;
      expect(item['type'], 'service');
      expect(item['durationMin'], 60);
      expect(find.text('Facial'), findsOneWidget);
    });

    testWidgets('invalid duration shows the server error', (tester) async {
      await pumpAdmin(
        tester,
        (b) => b.addCategory('Face'),
        salon: true,
      );
      await tester.tap(find.byKey(const Key('item-add')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('item-name')), 'Facial');
      await tester.enterText(find.byKey(const Key('item-price')), '120.00');
      await tester.enterText(find.byKey(const Key('item-duration')), '900');
      await tester.tap(find.byKey(const Key('item-save')));
      await tester.pumpAndSettle();
      expect(
        find.text('Duration must be between 5 and 600 minutes'),
        findsOneWidget,
      );
    });

    testWidgets('with VAT on, the admin picks the VAT rate', (tester) async {
      final backend = await pumpAdmin(
        tester,
        (b) => b.addCategory('Face'),
        salon: true,
        vat: true,
      );
      await tester.tap(find.byKey(const Key('item-add')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('item-name')),
        'Eyebrow tint',
      );
      await tester.enterText(find.byKey(const Key('item-price')), '25.00');
      await tester.tap(find.byKey(const Key('item-vat-rate')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('2.6 %').last);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('item-save')));
      await tester.pumpAndSettle();
      expect(backend.items.single['vatRate'], '2.6');
    });

    testWidgets('event offers every type, a duration only for services', (
      tester,
    ) async {
      await pumpAdmin(tester, (b) => b.addCategory('Drinks'));
      await tester.tap(find.byKey(const Key('item-add')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('item-type')), findsOneWidget);
      // Spec menu-catalog: item types no longer depend on a mode.
      expect(find.text('Service'), findsOneWidget);
      expect(find.byKey(const Key('item-duration')), findsNothing);
    });
  });
}
