package ch.paynash.pos.order;

import java.time.Instant;

/**
 * {@code voidedByName} and {@code voidedAtStationName} are set for a voided order (the station only if voided there).
 * An order has either a {@code tableLabel} (event mode) or a customer (salon mode, add-salon-mode design D3);
 * {@code customerName} is "Deleted customer" for an anonymized customer.
 */
public record Order(long id, String tableLabel, OrderStatus status, long sentBy, String sentByName, Instant sentAt,
		int version, String voidReason, String voidedByName, String voidedAtStationName, Long customerId,
		Integer customerNumber, String customerName) {
}
