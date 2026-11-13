package ch.paynash.pos.printing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import ch.paynash.pos.menu.ItemType;

/**
 * What a printer prints and what a print job is: production tickets of one item type, or invoice receipts
 * (add-salon-mode design D8). One active printer per purpose.
 */
public enum PrintPurpose {

	FOOD, DRINK, RECEIPT;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static PrintPurpose fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

	/** The production printer of an item type; services are never printed. */
	public static PrintPurpose of(ItemType type) {
		return switch (type) {
			case FOOD -> FOOD;
			case DRINK -> DRINK;
			case SERVICE -> throw new IllegalArgumentException("Services are never printed");
		};
	}

}
