package ch.paynash.pos.menu;

public record MenuItem(long id, long categoryId, String name, ItemType type, int priceRappen, String description,
		int displayOrder, boolean active, String imageRef) {
}
