package ch.paynash.pos.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How an installation runs (spec venue-mode, "Installation mode"). */
public enum Mode {

	EVENT, SALON;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static Mode fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
