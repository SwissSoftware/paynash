package ch.paynash.pos.printing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;

/**
 * Production ticket layout (spec ticket-printing, "Ticket content" and "Cancellation tickets"). Callers pass the
 * lines routed to one printer. Prices are left out on purpose.
 */
@Component
public class TicketRenderer {

	/** Characters per line at normal size on 80 mm paper; large text fits half of it. */
	static final int WIDTH = 48;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private final Clock clock;

	private final ZoneId zone;

	public TicketRenderer(Clock clock, @Value("${pos.time-zone}") ZoneId zone) {
		this.clock = clock;
		this.zone = zone;
	}

	/** The ticket for the lines of a sent order that go to one printer. */
	public Ticket order(Order order, List<OrderLine> lines) {
		var t = Ticket.builder()
			.large("Table " + order.tableLabel())
			.large("Order " + order.id())
			.large(order.sentByName())
			.large(format(order.sentAt()))
			.separator();
		lines(t, lines);
		return t.build();
	}

	/**
	 * A cancellation ticket for one printer: a single cancelled line, or all of the printer's lines that a void
	 * cancelled.
	 */
	public Ticket storno(Order order, List<OrderLine> lines, String reason, boolean orderVoided) {
		var t = Ticket.builder()
			.large("*** STORNO ***")
			.large("Table " + order.tableLabel())
			.large("Order " + order.id() + (orderVoided ? " voided" : ""))
			.large(format(clock.instant()))
			.separator();
		lines(t, lines);
		return t.separator().large("Reason: " + reason).build();
	}

	/** Says what the printer prints: the categories of its tickets and/or receipts (design D8). */
	public Ticket testTicket(Printer printer, List<PrinterRepository.CategoryRef> categories) {
		var t = Ticket.builder().large("Test print").large(printer.name()).large(format(clock.instant())).separator();
		if (printer.printsTickets()) {
			t.normal("Prints tickets: " + (categories.isEmpty() ? "no categories"
					: String.join(", ", categories.stream().map(PrinterRepository.CategoryRef::name).toList())));
		}
		if (printer.printsReceipts()) {
			t.normal("Prints receipts");
		}
		if (!printer.printsTickets() && !printer.printsReceipts()) {
			t.normal("Prints nothing (tickets and receipts are off)");
		}
		return t.normal("Umlauts: ä ö ü Ä Ö Ü é è à ç").build();
	}

	private static void lines(Ticket.Builder t, List<OrderLine> lines) {
		for (OrderLine line : lines) {
			t.large(line.qty() + " x " + line.name());
			if (line.note() != null) {
				t.large("  > " + line.note());
			}
		}
	}

	private String format(Instant instant) {
		return TIME.format(instant.atZone(zone));
	}

}
