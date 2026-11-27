package ch.paynash.pos.menu;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of a menu item. Items of all types can be mixed in one menu and one order (spec menu-catalog). Which
 * printer gets a line follows its category, not its type (spec ticket-printing, "Route lines by category").
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
