package ch.paynash.pos.printing;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.LongPredicate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.TicketPrinting;

/**
 * Writes the print jobs for order changes into the outbox, in the caller's transaction (design D3): at most one
 * ticket per printer, each with the lines of the categories assigned to that printer (spec ticket-printing, "Route
 * lines by category", change printer-category-routing design D3). The worker delivers them later; nothing here talks
 * to a printer.
 */
@Component
public class PrintJobs implements TicketPrinting {

	/** Old jobs from before printer-category-routing that were created without a printer. */
	static final String NO_PRINTER = "no printer configured";

	static final String NO_ACTIVE_PRINTER = "no active printer";

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

	/**
	 * Routes each line to the printers its category is assigned to, and records them so a later STORNO goes to the
	 * same printers. Lines of unassigned categories are not printed.
	 */
	@Override
	public void orderSent(Order order, List<OrderLine> lines) {
		Map<Long, List<Printer>> assigned = printers
			.ticketPrintersByItem(lines.stream().map(OrderLine::menuItemId).distinct().toList());
		Map<Printer, List<OrderLine>> byPrinter = byPrinter(lines, l -> assigned.getOrDefault(l.menuItemId(), List.of()));
		byPrinter.forEach((printer, ofPrinter) -> ofPrinter
			.forEach(line -> printers.recordLinePrinter(line.id(), printer.id())));
		byPrinter.forEach((printer, ofPrinter) -> create(order.id(), null, printer, PrintJob.Kind.NORMAL,
				renderer.order(order, ofPrinter), ofPrinter.size(), null, null));
	}

	@Override
	public void lineCancelled(Order order, OrderLine line) {
		storno(order, List.of(line), line.cancelReason(), false);
	}

	@Override
	public void orderVoided(Order order, List<OrderLine> lines) {
		storno(order, lines, order.voidReason(), true);
	}

	/** STORNO tickets go to the printers the lines were routed to when sent (design D4). */
	private void storno(Order order, List<OrderLine> lines, String reason, boolean orderVoided) {
		Map<Long, List<Printer>> routed = printers.printersByLine(lines.stream().map(OrderLine::id).toList());
		byPrinter(lines, l -> routed.getOrDefault(l.id(), List.of()))
			.forEach((printer, ofPrinter) -> create(order.id(), null, printer, PrintJob.Kind.STORNO,
					renderer.storno(order, ofPrinter, reason, orderVoided), ofPrinter.size(), null, null));
	}

	/**
	 * Queues the ticket again with a REPRINT banner, on the printer of the original ticket (design D4); it fails with
	 * "no active printer" if that printer is inactive now. The reprint keeps the original ticket number; a ticket that
	 * never had one (its printer was inactive) gets its first number now.
	 */
	@Transactional
	public PrintJob reprint(long orderId, long jobId) {
		PrintJob original = jobs.findForUpdate(jobId)
			.filter(j -> j.orderId() != null && j.orderId() == orderId)
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
		if (original.orderId() == null) {
			throw ApiException.conflict("Receipts are printed again as a copy of their invoice");
		}
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
		Printer printer = original.printerId() == null ? null : printers.findById(original.printerId()).orElse(null);
		if (printer == null) {
			return jobs.find(jobs.insertFailed(orderId, null, null, PrintJob.Kind.REPRINT, ticket.encode(), jobId,
					original.lineCount(), NO_PRINTER, clock.instant()))
				.orElseThrow();
		}
		return jobs.find(create(orderId, null, printer, PrintJob.Kind.REPRINT, ticket, original.lineCount(), jobId,
				original.ticketNo()))
			.orElseThrow();
	}

	/**
	 * Queues the ticket on {@code printer}. On an active printer a new ticket gets the printer's next number on top
	 * (design D3); {@code keepNo} is the number of the ticket being reprinted, whose content already shows it. On an
	 * inactive printer the ticket fails with "no active printer", without a number.
	 */
	private long create(Long orderId, Long invoiceId, Printer printer, PrintJob.Kind kind, Ticket ticket,
			int lineCount, Long reprintOf, Integer keepNo) {
		if (!printer.active()) {
			return jobs.insertFailed(orderId, invoiceId, printer.id(), kind, ticket.encode(), reprintOf, lineCount,
					NO_ACTIVE_PRINTER, clock.instant());
		}
		int no = keepNo != null ? keepNo : printers.nextTicketNo(printer.id());
		Ticket content = keepNo != null ? ticket : ticket.numbered(printer.name(), no);
		return jobs.insertPending(orderId, invoiceId, printer.id(), kind, content.encode(), reprintOf, no, lineCount,
				clock.instant());
	}

	// --- invoice receipts (add-salon-mode design D8)

	/**
	 * Queues an invoice receipt on every active printer with receipts on; if all of them are inactive, as failed "no
	 * active printer" on each (design D5). Returns no job when no printer has receipts on. Called inside the invoice
	 * transaction, so the receipt cannot be lost. {@code kind} is NORMAL when issuing and COPY when printed again.
	 */
	public List<PrintJob> queueReceipt(long invoiceId, PrintJob.Kind kind, Ticket receipt, int lineCount) {
		return targets(printers.receiptPrinters())
			.stream()
			.map(p -> jobs.find(create(null, invoiceId, p, kind, receipt, lineCount, null, null)).orElseThrow())
			.toList();
	}

	/** The invoice's receipt jobs, oldest first. */
	public List<PrintJob> receipts(long invoiceId) {
		return jobs.findByInvoice(invoiceId);
	}

	/**
	 * Marks the invoice's unclear receipts as resolved by printing a copy, so they no longer ask for a decision.
	 */
	public void resolveUnclearReceipts(long invoiceId) {
		for (PrintJob job : jobs.findByInvoice(invoiceId)) {
			if (job.needsResolution()) {
				jobs.resolve(job.id(), PrintJob.Resolution.REPRINT, null, clock.instant());
			}
		}
	}

	/**
	 * Groups lines by target printer, keeping their order within a ticket; printers are in the order given by
	 * {@code assigned} (by name). A line goes to its active printers; if all of them are inactive, to each of those so
	 * the ticket fails visibly; with no printer it is not printed.
	 */
	private static Map<Printer, List<OrderLine>> byPrinter(List<OrderLine> lines,
			Function<OrderLine, List<Printer>> assigned) {
		Map<Printer, List<OrderLine>> result = new TreeMap<>(
				Comparator.comparing((Printer p) -> p.name().toLowerCase()).thenComparingLong(Printer::id));
		for (OrderLine line : lines) {
			for (Printer printer : targets(assigned.apply(line))) {
				result.computeIfAbsent(printer, p -> new ArrayList<>()).add(line);
			}
		}
		return result;
	}

	/** The active printers, or all of them when none is active (each then gets a failed job). */
	private static List<Printer> targets(List<Printer> assigned) {
		List<Printer> active = assigned.stream().filter(Printer::active).toList();
		return active.isEmpty() ? assigned : active;
	}

}
