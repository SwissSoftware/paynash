package ch.paynash.pos.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.Money;
import ch.paynash.pos.menu.ItemType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Order taking for waiters. Sending accepts an {@code Idempotency-Key} header (design D5). Clients poll the order
 * history {@code GET /orders} and {@code GET /orders/{id}} with {@code If-None-Match} (design D4).
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Orders")
public class OrderController {

	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	public record OrderSummaryView(long id, String tableLabel, OrderStatus status, String waiter, Instant sentAt,
			String total) {
	}

	public record LineView(long id, long menuItemId, String name, ItemType type, String category, String unitPrice,
			int qty, String note, LineStatus status, String lineTotal, String cancelReason) {
	}

	public record OrderView(long id, String tableLabel, OrderStatus status, String waiter, Instant sentAt,
			int version, String total, List<LineView> lines, String voidReason) {

		static OrderView of(OrderService.OrderDetail d) {
			Order o = d.order();
			List<LineView> lines = d.lines()
				.stream()
				.map(l -> new LineView(l.id(), l.menuItemId(), l.name(), l.type(), l.category(),
						Money.format(l.unitPriceRappen()), l.qty(), l.note(), l.status(), Money.format(l.totalRappen()),
						l.cancelReason()))
				.toList();
			return new OrderView(o.id(), o.tableLabel(), o.status(), o.sentByName(), o.sentAt(), o.version(),
					Money.format(d.totalRappen()), lines, o.voidReason());
		}

	}

	public record LineRequest(@NotNull Long menuItemId,
			@NotNull @Min(value = 1, message = "Quantity must be 1 to 99") @Max(value = 99,
					message = "Quantity must be 1 to 99") Integer qty,
			@Size(max = 140, message = "Note must be at most 140 characters") String note) {
	}

	public record SendRequest(@NotBlank(message = "Table is required") String tableLabel,
			@NotEmpty(message = "The order has no lines") List<@Valid @NotNull LineRequest> lines) {
	}

	@GetMapping
	@Operation(summary = "Order history: orders sent on a day, newest first",
			description = "date is a local date (Europe/Zurich); defaults to today.")
	public ResponseEntity<List<OrderSummaryView>> history(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		var views = service.history(date)
			.stream()
			.map(o -> new OrderSummaryView(o.id(), o.tableLabel(), o.status(), o.waiterName(), o.sentAt(),
					Money.format(o.totalRappen())))
			.toList();
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(views);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Send an order",
			description = "Saves the order as sent and creates the print jobs in the same transaction. The table "
					+ "label is 1 to 10 letters or digits and stored upper-case. Name, type, category, and price "
					+ "are copied from the menu items. 409 with inactiveMenuItemIds if items were deactivated; "
					+ "nothing is saved then.")
	public OrderView send(@Valid @RequestBody SendRequest r, @AuthenticationPrincipal AuthenticatedStaff current) {
		var lines = r.lines()
			.stream()
			.map(l -> new OrderService.NewLine(l.menuItemId(), l.qty(), l.note()))
			.toList();
		return OrderView.of(service.send(r.tableLabel(), lines, current));
	}

	@GetMapping("/{id}")
	@Operation(summary = "An order with its lines")
	public ResponseEntity<OrderView> order(@PathVariable long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(OrderView.of(service.detail(id)));
	}

	@Bean
	static FilterRegistrationBean<ShallowEtagHeaderFilter> ordersEtagFilter() {
		var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/orders", "/orders/*");
		return registration;
	}

}
