import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:paynash_pos/api/api_client.dart';
import 'package:paynash_pos/auth/token_store.dart';
import 'package:paynash_pos/menu/item_visuals.dart';
import 'package:paynash_pos/menu/menu_api.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

Future<AppHarness> openMenu(WidgetTester tester, void Function(FakeBackend) seed) async {
  final app = AppHarness(tester);
  app.backend.addStaff('anna', 'waiter', '4711', displayName: 'Anna');
  seed(app.backend);
  await app.start();
  await app.login('anna', '4711');
  await tester.tap(find.text('Menu'));
  await tester.pumpAndSettle();
  return app;
}

void main() {
  testWidgets('shows active items as tiles with name and price', (tester) async {
    late int cola, tiramisu;
    await openMenu(tester, (b) {
      final drinks = b.addCategory('Drinks');
      cola = b.addItem(drinks, 'Cola', 'drink', '4.50');
      tiramisu = b.addItem(drinks, 'Tiramisu', 'food', '9.00', active: false);
    });

    expect(find.byKey(Key('menu-tile-$cola')), findsOneWidget);
    expect(find.text('CHF 4.50'), findsOneWidget);
    expect(find.byKey(Key('menu-tile-$tiramisu')), findsNothing);
  });

  testWidgets('item without image shows a placeholder with a food or drink icon',
      (tester) async {
    late int cola, pizza;
    await openMenu(tester, (b) {
      final c = b.addCategory('Mixed');
      cola = b.addItem(c, 'Cola', 'drink', '4.50');
      pizza = b.addItem(c, 'Margherita', 'food', '18.00');
    });

    final colaPlaceholder = find.byKey(Key('placeholder-$cola'));
    expect(colaPlaceholder, findsOneWidget);
    expect(find.descendant(of: colaPlaceholder, matching: find.byIcon(Icons.local_bar)),
        findsOneWidget);
    expect(
        find.descendant(
            of: find.byKey(Key('placeholder-$pizza')), matching: find.byIcon(Icons.restaurant)),
        findsOneWidget);
  });

  testWidgets('item with image loads it from the backend URL', (tester) async {
    await openMenu(tester, (b) {
      b.addItem(b.addCategory('Pizzas'), 'Margherita', 'food', '18.00',
          imageUrl: '/images/abc.webp');
    });

    final image = tester.widget<Image>(find.byType(Image));
    expect((image.image as NetworkImage).url, 'http://backend/images/abc.webp');
  });

  testWidgets('food and drink tiles carry different markers', (tester) async {
    late int cola, pizza;
    await openMenu(tester, (b) {
      final c = b.addCategory('Mixed');
      cola = b.addItem(c, 'Cola', 'drink', '4.50');
      pizza = b.addItem(c, 'Margherita', 'food', '18.00');
    });

    ItemTypeBadge badgeIn(int id) => tester.widget<ItemTypeBadge>(find.descendant(
        of: find.byKey(Key('menu-tile-$id')), matching: find.byType(ItemTypeBadge)));
    expect(badgeIn(cola).type.label, 'Drink');
    expect(badgeIn(pizza).type.label, 'Food');
    expect(badgeIn(cola).type.color, isNot(badgeIn(pizza).type.color));
  });

  testWidgets('category filter shows only that category', (tester) async {
    late int pizzas, cola, pizza;
    await openMenu(tester, (b) {
      final drinks = b.addCategory('Drinks');
      pizzas = b.addCategory('Pizzas');
      cola = b.addItem(drinks, 'Cola', 'drink', '4.50');
      pizza = b.addItem(pizzas, 'Margherita', 'food', '18.00');
    });

    await tester.tap(find.byKey(Key('category-chip-$pizzas')));
    await tester.pumpAndSettle();
    expect(find.byKey(Key('menu-tile-$pizza')), findsOneWidget);
    expect(find.byKey(Key('menu-tile-$cola')), findsNothing);

    await tester.tap(find.byKey(const Key('category-chip-all')));
    await tester.pumpAndSettle();
    expect(find.byKey(Key('menu-tile-$cola')), findsOneWidget);
  });

  test('unchanged menu is served from the local copy after a 304', () async {
    final backend = FakeBackend()..addStaff('anna', 'waiter', '4711');
    backend.addItem(backend.addCategory('Drinks'), 'Cola', 'drink', '4.50');
    backend.sessions['t'] = backend.staff.first;
    final api = MenuApi(ApiClient(
        baseUrl: 'http://backend',
        tokenStore: InMemoryTokenStore()..token = 't',
        httpClient: backend.client));

    final first = await api.load();
    final second = await api.load();
    expect(second, same(first));

    backend.addItem(backend.categories.first['id'] as int, 'Beer', 'drink', '6.00');
    final third = await api.load();
    expect(third.first.items.map((i) => i.name), ['Cola', 'Beer']);
  });
}
