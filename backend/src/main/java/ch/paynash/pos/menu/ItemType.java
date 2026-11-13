package ch.paynash.pos.menu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Decides which printer gets a line (spec ticket-printing). Services are sold in salon mode and never go to a
 * production printer (add-salon-mode).
 */
public enum ItemType {

	FOOD, DRINK, SERVICE;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static ItemType fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
