package ch.paynash.pos.printing;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.TicketPrinting;

/**
 * Writes the print jobs for order changes into the outbox, in the caller's transaction (design D3): at most one
 * ticket per printer, each with only the lines of that printer's type (spec ticket-printing, "Route lines by type").
 * The worker delivers them later; nothing here talks to a printer.
 */
@Component
public class PrintJobs implements TicketPrinting {

	static final String NO_PRINTER = "no printer configured";

	private final PrintJobRepository jobs;

	private final PrinterRepository printers;

	private final TicketRenderer renderer;

	private final Clock clock;

	public PrintJobs(PrintJobRepository jobs, PrinterRepository printers, TicketRenderer renderer, Clock clock) {
		this.jobs = jobs;
		this.printers = printers;
		this.renderer = renderer;
		this.clock = clock;
	}

	@Override
	public void orderSent(Order order, List<OrderLine> lines) {
		byType(lines).forEach((type, ofType) -> create(order.id(), type, PrintJob.Kind.NORMAL,
				renderer.order(order, ofType), null));
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		create(order.id(), line.type(), PrintJob.Kind.STORNO,
				renderer.storno(order, List.of(line), line.cancelReason(), false), null);
	}

	@Override
	public void orderVoided(Order order, List<OrderLine> lines) {
		byType(lines).forEach((type, ofType) -> create(order.id(), type, PrintJob.Kind.STORNO,
				renderer.storno(order, ofType, order.voidReason(), true), null));
	}

	/**
	 * Queues the ticket again with a REPRINT banner, for the printer that serves its type now (the paper may have
	 * been changed or another printer configured meanwhile).
	 */
	@Transactional
	public PrintJob reprint(long orderId, long jobId) {
		PrintJob original = jobs.find(jobId)
			.filter(j -> j.orderId() == orderId)
			.orElseThrow(() -> ApiException.notFound("Ticket"));
		if (original.status() == PrintJob.Status.PENDING) {
			throw ApiException.conflict("The ticket is still being printed");
		}
		Ticket ticket = Ticket.decode(original.content());
		if (original.kind() != PrintJob.Kind.REPRINT) {
			ticket = ticket.asReprint();
		}
		return jobs.find(create(orderId, original.itemType(), PrintJob.Kind.REPRINT, ticket, jobId)).orElseThrow();
	}

	private long create(long orderId, ItemType type, PrintJob.Kind kind, Ticket ticket, Long reprintOf) {
		return printers.findActiveFor(type)
			.map(p -> jobs.insertPending(orderId, type, p.id(), kind, ticket.encode(), reprintOf, clock.instant()))
			.orElseGet(() -> jobs.insertFailed(orderId, type, kind, ticket.encode(), reprintOf, NO_PRINTER,
					clock.instant()));
	}

	/** Drinks first, then food; lines keep their order within a ticket. */
	private static Map<ItemType, List<OrderLine>> byType(List<OrderLine> lines) {
		Map<ItemType, List<OrderLine>> result = new LinkedHashMap<>();
		for (ItemType type : List.of(ItemType.DRINK, ItemType.FOOD)) {
			List<OrderLine> ofType = lines.stream().filter(l -> l.type() == type).toList();
			if (!ofType.isEmpty()) {
				result.put(type, ofType);
			}
		}
		return result;
	}

}
