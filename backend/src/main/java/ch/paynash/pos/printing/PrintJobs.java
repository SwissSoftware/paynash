package ch.paynash.pos.printing;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

import org.springframework.http.HttpStatus;
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
				renderer.order(order, ofType), ofType.size(), null, null));
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		create(order.id(), line.type(), PrintJob.Kind.STORNO,
				renderer.storno(order, List.of(line), line.cancelReason(), false), 1, null, null);
	}

	@Override
	public void orderVoided(Order order, List<OrderLine> lines) {
		byType(lines).forEach((type, ofType) -> create(order.id(), type, PrintJob.Kind.STORNO,
				renderer.storno(order, ofType, order.voidReason(), true), ofType.size(), null, null));
	}

	/**
	 * Queues the ticket again with a REPRINT banner, for the printer that serves its type now (the paper may have
	 * been changed or another printer configured meanwhile). The reprint keeps the original ticket number; a ticket
	 * that never had one (no printer was configured) gets its first number now.
	 */
	@Transactional
	public PrintJob reprint(long orderId, long jobId) {
		PrintJob original = jobs.findForUpdate(jobId)
			.filter(j -> j.orderId() == orderId)
			.orElseThrow(() -> ApiException.notFound("Ticket"));
		return reprint(original, null);
	}

	/**
	 * Resolves an unclear ticket for a station (spec print-station, "Resolve unclear tickets"): "printed" sets it to
	 * printed without printing; "reprint" queues it again marked REPRINT. The job row is locked, so of two
	 * resolutions only the first takes effect and at most one reprint is queued. Returns the reprint, or the resolved
	 * ticket.
	 */
	@Transactional
	public PrintJob resolve(long jobId, PrintJob.Resolution resolution, long stationId, LongPredicate printerAllowed) {
		PrintJob job = lockForStation(jobId, printerAllowed);
		if (job.status() != PrintJob.Status.UNCLEAR) {
			throw ApiException.conflict("Only unclear tickets can be resolved");
		}
		if (job.resolution() != null) {
			throw ApiException.conflict("The ticket was already resolved").with("resolution",
					job.resolution().dbValue());
		}
		if (resolution == PrintJob.Resolution.PRINTED) {
			jobs.resolve(jobId, resolution, stationId, clock.instant());
			return jobs.find(jobId).orElseThrow();
		}
		return reprint(job, stationId);
	}

	/** A reprint requested at a station, for a ticket of one of its printers. */
	@Transactional
	public PrintJob reprintAtStation(long jobId, long stationId, LongPredicate printerAllowed) {
		return reprint(lockForStation(jobId, printerAllowed), stationId);
	}

	private PrintJob lockForStation(long jobId, LongPredicate printerAllowed) {
		PrintJob job = jobs.findForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Ticket"));
		if (job.printerId() == null || !printerAllowed.test(job.printerId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This station does not show that ticket's printer");
		}
		return job;
	}

	/** Reprinting an unclear ticket also resolves it, so the station no longer asks about it. */
	private PrintJob reprint(PrintJob original, Long stationId) {
		long orderId = original.orderId();
		long jobId = original.id();
		if (original.inProgress()) {
			throw ApiException.conflict("The ticket is still being printed");
		}
		Ticket ticket = Ticket.decode(original.content());
		if (original.kind() != PrintJob.Kind.REPRINT) {
			ticket = ticket.asReprint();
		}
		if (original.needsResolution()) {
			jobs.resolve(jobId, PrintJob.Resolution.REPRINT, stationId, clock.instant());
		}
		return jobs.find(create(orderId, original.itemType(), PrintJob.Kind.REPRINT, ticket, original.lineCount(), jobId,
				original.ticketNo()))
			.orElseThrow();
	}

	/**
	 * Queues the ticket for the active printer of its type. A new ticket gets the printer's next number on top
	 * (design D3); {@code keepNo} is the number of the ticket being reprinted, whose content already shows it.
	 */
	private long create(long orderId, ItemType type, PrintJob.Kind kind, Ticket ticket, int lineCount, Long reprintOf,
			Integer keepNo) {
		return printers.findActiveFor(type).map(p -> {
			int no = keepNo != null ? keepNo : printers.nextTicketNo(p.id());
			Ticket content = keepNo != null ? ticket : ticket.numbered(p.name(), no);
			return jobs.insertPending(orderId, type, p.id(), kind, content.encode(), reprintOf, no, lineCount,
					clock.instant());
		})
			.orElseGet(() -> jobs.insertFailed(orderId, type, kind, ticket.encode(), reprintOf, lineCount, NO_PRINTER,
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
