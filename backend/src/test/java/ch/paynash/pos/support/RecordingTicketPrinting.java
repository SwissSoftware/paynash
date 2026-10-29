package ch.paynash.pos.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.TicketPrinting;

/** Records what order taking hands to ticket printing. */
public class RecordingTicketPrinting implements TicketPrinting {

	public record Printed(Order order, List<OrderLine> lines) {
	}

	public final List<Printed> sent = new CopyOnWriteArrayList<>();

	public final List<OrderLine> cancellations = new CopyOnWriteArrayList<>();

	public final List<Printed> voids = new CopyOnWriteArrayList<>();

	@Override
	public void orderSent(Order order, List<OrderLine> lines) {
		sent.add(new Printed(order, lines));
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		cancellations.add(line);
	}

	@Override
	public void orderVoided(Order order, List<OrderLine> lines) {
		voids.add(new Printed(order, lines));
	}

	public void reset() {
		sent.clear();
		cancellations.clear();
		voids.clear();
	}

}
