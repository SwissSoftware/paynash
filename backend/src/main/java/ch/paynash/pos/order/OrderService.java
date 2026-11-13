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
import ch.paynash.pos.customer.Customer;
import ch.paynash.pos.customer.CustomerRepository;
import ch.paynash.pos.menu.Category;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuItem;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintJobRepository;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.SettingsService;

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

	private final SettingsService settings;

	private final CustomerRepository customers;

	public OrderService(OrderRepository orders, MenuRepository menu, TicketPrinting printing,
			PrintJobRepository printJobs, Clock clock, @Value("${pos.time-zone}") ZoneId zone,
			SettingsService settings, CustomerRepository customers) {
		this.settings = settings;
		this.customers = customers;
		this.orders = orders;
		this.menu = menu;
		this.printing = printing;
		this.printJobs = printJobs;
		this.clock = clock;
		this.zone = zone;
	}

	/** An order with everything the order history shows, including its tickets. */
	/** {@code invoice} is the invoice a salon order shows, or null (spec order-taking, salon mode). */
	public record OrderDetail(Order order, List<OrderLine> lines, List<PrintJob> tickets,
			OrderRepository.InvoiceRef invoice) {

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
		return new OrderDetail(order, orders.findLines(orderId), printJobs.findByOrder(orderId),
				order.customerId() == null ? null : orders.shownInvoice(orderId).orElse(null));
	}

	// --- sending

	/**
	 * Saves the order as sent and hands all lines to ticket printing in the same transaction. If any item is inactive
	 * the whole order is rejected, listing those items, so the waiter never sends half an order. Labels are stored
	 * upper-case.
	 */
	@Transactional
	public OrderDetail send(String tableLabel, Long customerId, List<NewLine> lines, AuthenticatedStaff staff) {
		// Shared lock: a mode change waits for this send, and this send waits for a running mode change (venue-mode).
		boolean salon = settings.lockModeForOrder() == Mode.SALON;
		String label = null;
		Customer customer = null;
		if (salon) {
			// Salon orders belong to a customer, not a table (spec order-taking, "Confirm an order for a customer").
			if (tableLabel != null && !tableLabel.isBlank()) {
				throw ApiException.validation("tableLabel", "Salon orders have no table");
			}
			if (customerId == null) {
				throw ApiException.validation("customerId", "Customer is required");
			}
			customer = customers.lockForOrder(customerId)
				.orElseThrow(() -> ApiException.validation("customerId", "Customer does not exist"));
			if (!customer.active()) {
				throw ApiException.conflict(customer.displayName() + " is no longer active")
					.with("inactiveCustomerId", customer.id());
			}
		}
		else {
			if (customerId != null) {
				throw ApiException.validation("customerId", "Orders in event mode have no customer");
			}
			if (tableLabel == null || tableLabel.isBlank()) {
				throw ApiException.validation("tableLabel", "Table is required");
			}
			label = TableLabels.normalize(tableLabel, "tableLabel", "Table");
		}
		if (lines == null || lines.isEmpty()) {
			throw ApiException.validation("lines", "The order has no lines");
		}
		Map<Long, MenuItem> items = new HashMap<>();
		List<MenuItem> inactive = new ArrayList<>();
		for (NewLine line : lines) {
			MenuItem item = items.computeIfAbsent(line.menuItemId(), id -> menu.findItem(id)
				.orElseThrow(() -> ApiException.validation("lines", "Menu item " + id + " does not exist")));
			if ((item.type() == ItemType.SERVICE) != salon) {
				throw ApiException.validation("lines", salon ? item.name() + " is not a service"
						: item.name() + " is a service and can only be sold in salon mode");
			}
			if (!item.active() && !inactive.contains(item)) {
				inactive.add(item);
			}
		}
		if (!inactive.isEmpty()) {
			throw ApiException
				.conflict(String.join(", ", inactive.stream().map(MenuItem::name).toList()) + " no longer available")
				.with("inactiveMenuItemIds", inactive.stream().map(MenuItem::id).toList());
		}
		long orderId = orders.insertSent(label, customer == null ? null : customer.id(), staff.id(), clock.instant());
		for (NewLine line : lines) {
			MenuItem item = items.get(line.menuItemId());
			String category = menu.findCategory(item.categoryId()).map(Category::name).orElseThrow();
			orders.insertLine(orderId, item.id(), item.name(), item.type(), category, item.priceRappen(),
					item.vatRatePermille(), line.qty(), blankToNull(line.note()));
		}
		OrderDetail detail = detail(orderId);
		printing.orderSent(detail.order(), detail.lines());
		return detail(orderId);
	}

	// --- cancelling (admins, or staff confirming at a station)

	/**
	 * A sent line stays in the order as cancelled, no longer counts, and triggers a cancellation ticket.
	 * {@code staffId} is who cancelled; {@code stationId} the station where it was confirmed, or null.
	 */
	@Transactional
	public OrderDetail cancelLine(long orderId, long lineId, String reason, long staffId, Long stationId) {
		Order order = lockSent(orderId);
		OrderLine line = orders.findLine(orderId, lineId).orElseThrow(() -> ApiException.notFound("Order line"));
		if (line.status() == LineStatus.CANCELLED) {
			throw ApiException.conflict("The line is already cancelled");
		}
		orders.cancelLine(lineId, reason.trim(), staffId, stationId, clock.instant());
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
	public OrderDetail voidOrder(long orderId, String reason, Integer version, long staffId, Long stationId) {
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
		orders.cancelSentLines(orderId, reason.trim(), staffId, stationId, now);
		orders.voidOrder(orderId, staffId, stationId, reason.trim(), now);
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
		// An invoice is final, so its order stays as invoiced (spec invoicing, "Invoices are final"). Issuing takes
		// the same order lock, so the two cannot overlap.
		if (order.customerId() != null) {
			orders.shownInvoice(orderId).filter(i -> !"cancelled".equals(i.status())).ifPresent(i -> {
				throw ApiException.conflict("The order has invoice " + i.number() + ", cancel the invoice first")
					.with("invoiceId", i.id());
			});
		}
		return order;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
