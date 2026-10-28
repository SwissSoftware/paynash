package ch.paynash.pos.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.OrderRound;
import ch.paynash.pos.order.TicketPrinting;

/** Records what order taking hands to ticket printing. */
public class RecordingTicketPrinting implements TicketPrinting {

	public record SentRound(Order order, OrderRound round, List<OrderLine> lines) {
	}

	public final List<SentRound> rounds = new CopyOnWriteArrayList<>();

	public final List<OrderLine> cancellations = new CopyOnWriteArrayList<>();

	@Override
	public void roundSent(Order order, OrderRound round, List<OrderLine> lines) {
		rounds.add(new SentRound(order, round, lines));
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		cancellations.add(line);
	}

	public void reset() {
		rounds.clear();
		cancellations.clear();
	}

}
