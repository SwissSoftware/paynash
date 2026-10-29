package ch.paynash.pos.order;

import java.util.List;

/**
 * Hands sent orders and cancelled lines to ticket printing (spec ticket-printing). Called inside the order
 * transaction, so print jobs are stored atomically with the order change and survive restarts (design D3).
 * Implementations must only write jobs, never talk to a printer.
 */
public interface TicketPrinting {

	void orderSent(Order order, List<OrderLine> lines);

	void lineCancelled(Order order, OrderLine line);

	/** {@code lines} are the lines the void cancelled; lines cancelled earlier are not included. */
	void orderVoided(Order order, List<OrderLine> lines);

}
