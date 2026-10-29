package ch.paynash.pos.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lines are sent together with their order and can afterwards only be cancelled. */
public enum LineStatus {

	SENT, CANCELLED;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static LineStatus fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
