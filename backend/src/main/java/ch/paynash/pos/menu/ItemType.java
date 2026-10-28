package ch.paynash.pos.menu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Decides which printer gets a line (spec ticket-printing). */
public enum ItemType {

	FOOD, DRINK;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static ItemType fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
