package ch.paynash.pos.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.menu.Category;
import ch.paynash.pos.menu.MenuItem;
import ch.paynash.pos.menu.MenuRepository;

/**
 * Order taking (spec order-taking). Every change locks the order row first, so concurrent changes to one order are
 * serialised instead of overwriting each other, and a closed order cannot be changed by a request that started
 * before it was closed (design D4).
 */
@Service
public class OrderService {

	private static final Pattern TABLE_LABEL = Pattern.compile("[A-Za-z0-9]{1,10}");

	private final OrderRepository orders;

	private final MenuRepository menu;

	private final TicketPrinting printing;

	private final Clock clock;

	public OrderService(OrderRepository orders, MenuRepository menu, TicketPrinting printing, Clock clock) {
		this.orders = orders;
		this.menu = menu;
		this.printing = printing;
		this.clock = clock;
	}

	/** An order with everything the order screen shows. */
	public record OrderDetail(Order order, List<OrderLine> lines, List<OrderRound> rounds) {

		public long totalRappen() {
			return OrderLine.totalRappen(lines);
		}

	}

	public record OpenResult(OrderDetail detail, boolean created) {
	}

	/** {@code round} is null when there was nothing to send. */
	public record SendResult(OrderDetail detail, OrderRound round) {
	}

	// --- reading

	public List<OrderRepository.OpenOrderSummary> openOrders() {
		return orders.findOpenSummaries();
	}

	public OrderDetail detail(long orderId) {
		Order order = orders.find(orderId).orElseThrow(() -> ApiException.notFound("Order"));
		return new OrderDetail(order, orders.findLines(orderId), orders.findRounds(orderId));
	}

	// --- opening

	/** Opens an order for the table, or returns the table's existing open order. Labels are stored upper-case. */
	@Transactional
	public OpenResult open(String tableLabel, AuthenticatedStaff staff) {
		String label = tableLabel == null ? "" : tableLabel.trim().toUpperCase(Locale.ROOT);
		if (!TABLE_LABEL.matcher(label).matches()) {
			throw ApiException.validation("tableLabel", "Table must be 1 to 10 letters or digits");
		}
		// The insert and the lookup can race with another device closing the table's order; retrying settles it.
		for (int attempt = 0; attempt < 3; attempt++) {
			var created = orders.insertOpenIfAbsent(label, staff.id(), clock.instant());
			if (created.isPresent()) {
				return new OpenResult(detail(created.get()), true);
			}
			var existing = orders.findOpenByTable(label);
			if (existing.isPresent()) {
				return new OpenResult(detail(existing.get().id()), false);
			}
		}
		throw ApiException.conflict("The table is being changed on another device. Please try again.");
	}

	// --- unsent lines

	@Transactional
	public OrderDetail addLine(long orderId, long menuItemId, int qty, String note, AuthenticatedStaff staff) {
		lockOpen(orderId);
		MenuItem item = menu.findItem(menuItemId)
			.orElseThrow(() -> ApiException.validation("menuItemId", "Menu item does not exist"));
		if (!item.active()) {
			throw ApiException.conflict(item.name() + " is no longer available").with("menuItemInactive", true);
		}
		String category = menu.findCategory(item.categoryId()).map(Category::name).orElseThrow();
		orders.insertLine(orderId, item.id(), item.name(), item.type(), category, item.priceRappen(), qty,
				blankToNull(note), staff.id(), clock.instant());
		orders.incrementVersion(orderId);
		return detail(orderId);
	}

	/**
	 * {@code version} is the line version the client last saw. A mismatch means another device changed the line
	 * meanwhile; the change is rejected instead of silently overwriting it.
	 */
	@Transactional
	public OrderDetail updateLine(long orderId, long lineId, int qty, String note, int version) {
		lockOpen(orderId);
		OrderLine line = unsentLine(orderId, lineId);
		if (line.version() != version) {
			throw ApiException.conflict("The line was changed on another device. Please check it and try again.");
		}
		orders.updateUnsentLine(lineId, qty, blankToNull(note));
		orders.incrementVersion(orderId);
		return detail(orderId);
	}

	@Transactional
	public OrderDetail removeLine(long orderId, long lineId) {
		lockOpen(orderId);
		unsentLine(orderId, lineId);
		orders.deleteUnsentLine(lineId);
		orders.incrementVersion(orderId);
		return detail(orderId);
	}

	// --- sending and cancelling

	/**
	 * Sends all unsent lines as one round and hands them to ticket printing in the same transaction. Does nothing
	 * when there are no unsent lines, so a repeated send cannot create a second round.
	 */
	@Transactional
	public SendResult send(long orderId, AuthenticatedStaff staff) {
		Order order = lockOpen(orderId);
		boolean hasUnsent = orders.findLines(orderId).stream().anyMatch(l -> l.status() == LineStatus.UNSENT);
		if (!hasUnsent) {
			return new SendResult(detail(orderId), null);
		}
		OrderRound round = orders.insertRound(orderId, staff.id(), clock.instant());
		orders.markUnsentAsSent(orderId, round.id());
		orders.incrementVersion(orderId);
		OrderDetail detail = detail(orderId);
		printing.roundSent(order, round, detail.lines().stream().filter(l -> inRound(l, round)).toList());
		return new SendResult(detail, round);
	}

	/** A sent line stays in the order as cancelled, no longer counts, and triggers a cancellation ticket. */
	@Transactional
	public OrderDetail cancelLine(long orderId, long lineId, String reason, AuthenticatedStaff staff) {
		Order order = lockOpen(orderId);
		OrderLine line = orders.findLine(orderId, lineId).orElseThrow(() -> ApiException.notFound("Order line"));
		switch (line.status()) {
			case UNSENT -> throw ApiException.conflict("The line was not sent yet; remove it instead");
			case CANCELLED -> throw ApiException.conflict("The line is already cancelled");
			case SENT -> {
			}
		}
		Instant now = clock.instant();
		orders.cancelLine(lineId, reason.trim(), staff.id(), now);
		orders.incrementVersion(orderId);
		printing.lineCancelled(order, orders.findLine(orderId, lineId).orElseThrow());
		return detail(orderId);
	}

	// --- admin

	/**
	 * {@code version}, if given, is the order version the admin last saw; a mismatch rejects the void so changes made
	 * meanwhile are not discarded unseen.
	 */
	@Transactional
	public OrderDetail voidOrder(long orderId, String reason, Integer version, AuthenticatedStaff admin) {
		Order order = lockOpen(orderId);
		if (version != null && version != order.version()) {
			throw ApiException.conflict("The order was changed meanwhile. Please check it and try again.");
		}
		orders.voidOrder(orderId, admin.id(), reason.trim(), clock.instant());
		return detail(orderId);
	}

	// --- helpers

	private Order lockOpen(long orderId) {
		Order order = orders.findForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
		if (order.status() != OrderStatus.OPEN) {
			throw ApiException.conflict("The order is " + order.status().dbValue() + " and can no longer be changed")
				.with("orderStatus", order.status().dbValue());
		}
		return order;
	}

	private OrderLine unsentLine(long orderId, long lineId) {
		OrderLine line = orders.findLine(orderId, lineId).orElseThrow(() -> ApiException.notFound("Order line"));
		if (line.status() != LineStatus.UNSENT) {
			throw ApiException.conflict("Sent lines cannot be changed; cancel them instead");
		}
		return line;
	}

	static boolean inRound(OrderLine line, OrderRound round) {
		return line.roundId() != null && line.roundId() == round.id();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
