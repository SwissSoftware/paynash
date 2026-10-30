package ch.paynash.pos.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.TicketPrinting;
import ch.paynash.pos.printing.PrintJobs;

/** Records what order taking hands to ticket printing, then writes the print jobs as usual. */
public class RecordingTicketPrinting implements TicketPrinting {

	private final PrintJobs printJobs;

	public RecordingTicketPrinting(PrintJobs printJobs) {
		this.printJobs = printJobs;
	}

	public record Printed(Order order, List<OrderLine> lines) {
	}

	public final List<Printed> sent = new CopyOnWriteArrayList<>();

	public final List<OrderLine> cancellations = new CopyOnWriteArrayList<>();

	public final List<Printed> voids = new CopyOnWriteArrayList<>();

	@Override
	public void orderSent(Order order, List<OrderLine> lines) {
		sent.add(new Printed(order, lines));
		printJobs.orderSent(order, lines);
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		cancellations.add(line);
		printJobs.lineCancelled(order, line);
	}

	@Override
	public void orderVoided(Order order, List<OrderLine> lines) {
		voids.add(new Printed(order, lines));
		printJobs.orderVoided(order, lines);
	}

	public void reset() {
		sent.clear();
		cancellations.clear();
		voids.clear();
	}

}
