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
 * lines of one item type, so each ticket goes to exactly one printer. Prices are left out on purpose.
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

	/** The ticket for the lines of one type of a sent order. */
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
	 * A cancellation ticket for lines of one type: a single cancelled line, or all lines of the type that a void
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

	public Ticket testTicket(Printer printer) {
		return Ticket.builder()
			.large("Test print")
			.large(printer.name())
			.large(format(clock.instant()))
			.separator()
			.normal("Prints " + printer.itemType().dbValue() + " tickets")
			.normal("Umlauts: ä ö ü Ä Ö Ü é è à ç")
			.build();
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
