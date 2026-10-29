package ch.paynash.pos.order;

import java.time.Instant;

public record Order(long id, String tableLabel, OrderStatus status, long sentBy, String sentByName, Instant sentAt,
		int version, String voidReason) {
}
