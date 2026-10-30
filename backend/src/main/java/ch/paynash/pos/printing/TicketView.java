package ch.paynash.pos.printing;

import java.time.Instant;

import ch.paynash.pos.menu.ItemType;

/**
 * A ticket of an order as the app shows it. {@code printer} is null when no printer was configured;
 * {@code reprinted} means a later reprint replaces this ticket.
 */
public record TicketView(long id, String kind, ItemType type, String printer, String status, String error,
		Instant createdAt, Long reprintOf, boolean reprinted) {

	public static TicketView of(PrintJob j) {
		return new TicketView(j.id(), j.kind().dbValue(), j.itemType(), j.printerName(), j.status().dbValue(),
				j.lastError(), j.createdAt(), j.reprintOf(), j.reprinted());
	}

}
