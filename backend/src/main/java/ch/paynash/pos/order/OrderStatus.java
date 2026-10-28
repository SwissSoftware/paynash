package ch.paynash.pos.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {

	OPEN, PAID, VOIDED;

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static OrderStatus fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
