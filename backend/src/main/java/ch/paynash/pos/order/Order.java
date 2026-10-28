package ch.paynash.pos.order;

import java.time.Instant;

public record Order(long id, String tableLabel, OrderStatus status, long openedBy, String openedByName,
		Instant openedAt, int version, String voidReason) {
}
