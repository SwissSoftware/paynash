package ch.paynash.pos.order;

import java.time.Instant;

/**
 * {@code voidedByName} and {@code voidedAtStationName} are set for a voided order (the station only if voided there).
 * An order may have a {@code tableLabel}, a customer, both, or neither, depending on the features enabled when it was
 * sent (change configurable-business-features, design D3);
 * {@code customerName} is "Deleted customer" for an anonymized customer.
 */
public record Order(long id, String tableLabel, OrderStatus status, long sentBy, String sentByName, Instant sentAt,
		int version, String voidReason, String voidedByName, String voidedAtStationName, Long customerId,
		Integer customerNumber, String customerName) {
}
