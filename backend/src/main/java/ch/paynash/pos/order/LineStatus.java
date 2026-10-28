package ch.paynash.pos.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lines start unsent and can be edited; sending makes them read-only; sent lines can only be cancelled. */
public enum LineStatus {

	UNSENT, SENT, CANCELLED;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static LineStatus fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
