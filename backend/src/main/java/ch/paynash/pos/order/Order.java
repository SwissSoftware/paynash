package ch.paynash.pos.order;

import java.time.Instant;

/** {@code voidedByName} and {@code voidedAtStationName} are set for a voided order (the station only if voided there). */
public record Order(long id, String tableLabel, OrderStatus status, long sentBy, String sentByName, Instant sentAt,
		int version, String voidReason, String voidedByName, String voidedAtStationName) {
}
