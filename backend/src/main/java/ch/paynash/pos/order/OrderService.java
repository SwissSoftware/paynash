package ch.paynash.pos.order;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.common.TableLabels;
import ch.paynash.pos.menu.Category;
import ch.paynash.pos.menu.MenuItem;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintJobRepository;

/**
 * Order taking (spec order-taking). An order is built on the device and sent in one request; a sent order is final
 * except for cancelling lines and voiding it. Both lock the order row first, so they cannot cancel the same line
 * twice (design D4).
 */
@Service
public class OrderService {

	private final OrderRepository orders;

	private final MenuRepository menu;

	private final TicketPrinting printing;

	private final PrintJobRepository printJobs;

	private final Clock clock;

	private final ZoneId zone;

	public OrderService(OrderRepository orders, MenuRepository menu, TicketPrinting printing,
			PrintJobRepository printJobs, Clock clock, @Value("${pos.time-zone}") ZoneId zone) {
		this.orders = orders;
		this.menu = menu;
		this.printing = printing;
		this.printJobs = printJobs;
		this.clock = clock;
		this.zone = zone;
	}

	/** An order with everything the order history shows, including its tickets. */
	public record OrderDetail(Order order, List<OrderLine> lines, List<PrintJob> tickets) {

		public long totalRappen() {
			return OrderLine.totalRappen(lines);
		}

	}

	public record NewLine(long menuItemId, int qty, String note) {
	}

	// --- reading

	/** Orders sent on {@code day} in the restaurant's time zone, or today if {@code day} is null. */
	public List<OrderRepository.OrderSummary> history(LocalDate day) {
		LocalDate d = day != null ? day : LocalDate.ofInstant(clock.instant(), zone);
		return orders.findSummaries(d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant());
	}

	public OrderDetail detail(long orderId) {
		Order order = orders.find(orderId).orElseThrow(() -> ApiException.notFound("Order"));
		return new OrderDetail(order, orders.findLines(orderId), printJobs.findByOrder(orderId));
	}

	// --- sending

	/**
	 * Saves the order as sent and hands all lines to ticket printing in the same transaction. If any item is inactive
	 * the whole order is rejected, listing those items, so the waiter never sends half an order. Labels are stored
	 * upper-case.
	 */
	@Transactional
	public OrderDetail send(String tableLabel, List<NewLine> lines, AuthenticatedStaff staff) {
		String label = TableLabels.normalize(tableLabel, "tableLabel", "Table");
		if (lines == null || lines.isEmpty()) {
			throw ApiException.validation("lines", "The order has no lines");
		}
		Map<Long, MenuItem> items = new HashMap<>();
		List<MenuItem> inactive = new ArrayList<>();
		for (NewLine line : lines) {
			MenuItem item = items.computeIfAbsent(line.menuItemId(), id -> menu.findItem(id)
				.orElseThrow(() -> ApiException.validation("lines", "Menu item " + id + " does not exist")));
			if (!item.active() && !inactive.contains(item)) {
				inactive.add(item);
			}
		}
		if (!inactive.isEmpty()) {
			throw ApiException
				.conflict(String.join(", ", inactive.stream().map(MenuItem::name).toList()) + " no longer available")
				.with("inactiveMenuItemIds", inactive.stream().map(MenuItem::id).toList());
		}
		long orderId = orders.insertSent(label, staff.id(), clock.instant());
		for (NewLine line : lines) {
			MenuItem item = items.get(line.menuItemId());
			String category = menu.findCategory(item.categoryId()).map(Category::name).orElseThrow();
			orders.insertLine(orderId, item.id(), item.name(), item.type(), category, item.priceRappen(), line.qty(),
					blankToNull(line.note()));
		}
		OrderDetail detail = detail(orderId);
		printing.orderSent(detail.order(), detail.lines());
		return detail(orderId);
	}

	// --- admin

	/** A sent line stays in the order as cancelled, no longer counts, and triggers a cancellation ticket. */
	@Transactional
	public OrderDetail cancelLine(long orderId, long lineId, String reason, AuthenticatedStaff admin) {
		Order order = lockSent(orderId);
		OrderLine line = orders.findLine(orderId, lineId).orElseThrow(() -> ApiException.notFound("Order line"));
		if (line.status() == LineStatus.CANCELLED) {
			throw ApiException.conflict("The line is already cancelled");
		}
		orders.cancelLine(lineId, reason.trim(), admin.id(), clock.instant());
		orders.incrementVersion(orderId);
		printing.lineCancelled(order, orders.findLine(orderId, lineId).orElseThrow());
		return detail(orderId);
	}

	/**
	 * Voids the order and cancels its remaining lines with the void reason, so the kitchen and bar get cancellation
	 * tickets for exactly those. {@code version}, if given, is the order version the admin last saw; a mismatch
	 * rejects the void so cancellations made meanwhile are not overlooked.
	 */
	@Transactional
	public OrderDetail voidOrder(long orderId, String reason, Integer version, AuthenticatedStaff admin) {
		Order order = lockSent(orderId);
		if (version != null && version != order.version()) {
			throw ApiException.conflict("The order was changed meanwhile. Please check it and try again.");
		}
		List<Long> toCancel = orders.findLines(orderId)
			.stream()
			.filter(l -> l.status() == LineStatus.SENT)
			.map(OrderLine::id)
			.toList();
		Instant now = clock.instant();
		orders.cancelSentLines(orderId, reason.trim(), admin.id(), now);
		orders.voidOrder(orderId, admin.id(), reason.trim(), now);
		OrderDetail detail = detail(orderId);
		printing.orderVoided(detail.order(), detail.lines().stream().filter(l -> toCancel.contains(l.id())).toList());
		return detail(orderId);
	}

	// --- helpers

	private Order lockSent(long orderId) {
		Order order = orders.findForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
		if (order.status() != OrderStatus.SENT) {
			throw ApiException.conflict("The order is " + order.status().dbValue() + " and can no longer be changed")
				.with("orderStatus", order.status().dbValue());
		}
		return order;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
