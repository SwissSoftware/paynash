package ch.paynash.pos.staff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {

	ADMIN, WAITER;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static Role fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
