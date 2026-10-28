package ch.paynash.pos.order;

import java.util.List;

/**
 * Hands sent rounds and cancelled lines to ticket printing (spec ticket-printing). Called inside the order
 * transaction, so print jobs are stored atomically with the order change and survive restarts (design D3).
 * Implementations must only write jobs, never talk to a printer.
 */
public interface TicketPrinting {

	/** {@code lines} are exactly the lines sent in this round. */
	void roundSent(Order order, OrderRound round, List<OrderLine> lines);

	void lineCancelled(Order order, OrderLine line);

}
