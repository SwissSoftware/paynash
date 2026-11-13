package ch.paynash.pos.printing;

import java.time.Instant;

/**
 * A ticket of an order as the app shows it. {@code printer} is null when no printer was configured;
 * {@code reprinted} means a later reprint replaces this ticket. {@code status} is pending, printing, printed, unclear,
 * or failed; {@code waitingReason} says why a pending ticket waits and {@code error} what went wrong with an unclear
 * or failed one. {@code label} is the running ticket number, e.g. "BAR 0142". {@code resolution} is set once an
 * unclear ticket was resolved.
 */
public record TicketView(long id, String kind, PrintPurpose type, String printer, String status, String waitingReason,
		String error, String label, String resolution, Instant createdAt, Long reprintOf, boolean reprinted) {

	public static TicketView of(PrintJob j) {
		return new TicketView(j.id(), j.kind().dbValue(), j.purpose(), j.printerName(), j.status().dbValue(),
				j.waitingReason(), j.lastError(),
				j.ticketNo() == null || j.printerName() == null ? null : Ticket.label(j.printerName(), j.ticketNo()),
				j.resolution() == null ? null : j.resolution().dbValue(), j.createdAt(), j.reprintOf(), j.reprinted());
	}

}
