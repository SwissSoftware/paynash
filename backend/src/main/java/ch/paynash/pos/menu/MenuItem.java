package ch.paynash.pos.menu;

/** {@code durationMin} is only set for services; {@code vatRatePermille} is in tenths of a percent (81 = 8.1 %). */
public record MenuItem(long id, long categoryId, String name, ItemType type, int priceRappen, String description,
		int displayOrder, boolean active, String imageRef, Integer durationMin, int vatRatePermille) {
}
