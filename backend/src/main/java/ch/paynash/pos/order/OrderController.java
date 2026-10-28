package ch.paynash.pos.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Order taking for waiters. Mutating requests accept an {@code Idempotency-Key} header (design D5). Clients poll
 * {@code GET /orders} and {@code GET /orders/{id}} with {@code If-None-Match} (design D4).
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Orders")
public class OrderController {

	private final OrderService service;

	private final Clock clock;

	public OrderController(OrderService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	public record OpenOrderView(long id, String tableLabel, String waiter, String total, Instant openedAt,
			long ageMinutes, int unsentLines) {
	}

	public record LineView(long id, long menuItemId, String name, ItemType type, String category, String unitPrice,
			int qty, String note, LineStatus status, Integer roundNumber, String lineTotal, int version,
			String cancelReason) {
	}

	public record RoundView(long id, int number, String sentBy, Instant sentAt) {

		static RoundView of(OrderRound r) {
			return new RoundView(r.id(), r.number(), r.sentByName(), r.sentAt());
		}

	}

	public record OrderView(long id, String tableLabel, OrderStatus status, String waiter, Instant openedAt,
			int version, String total, boolean hasUnsentLines, List<LineView> lines, List<RoundView> rounds,
			String voidReason) {

		static OrderView of(OrderService.OrderDetail d) {
			Order o = d.order();
			List<LineView> lines = d.lines().stream().map(l -> {
				Integer round = d.rounds().stream()
					.filter(r -> OrderService.inRound(l, r))
					.map(OrderRound::number)
					.findFirst()
					.orElse(null);
				return new LineView(l.id(), l.menuItemId(), l.name(), l.type(), l.category(),
						Money.format(l.unitPriceRappen()), l.qty(), l.note(), l.status(), round,
						Money.format(l.totalRappen()), l.version(), l.cancelReason());
			}).toList();
			boolean unsent = d.lines().stream().anyMatch(l -> l.status() == LineStatus.UNSENT);
			return new OrderView(o.id(), o.tableLabel(), o.status(), o.openedByName(), o.openedAt(), o.version(),
					Money.format(d.totalRappen()), unsent, lines, d.rounds().stream().map(RoundView::of).toList(),
					o.voidReason());
		}

	}

	/** {@code round} is null when there were no unsent lines. */
	public record SendView(RoundView round, OrderView order) {
	}

	public record OpenRequest(@NotBlank(message = "Table is required") String tableLabel) {
	}

	public record AddLineRequest(@NotNull Long menuItemId,
			@NotNull @Min(value = 1, message = "Quantity must be 1 to 99") @Max(value = 99,
					message = "Quantity must be 1 to 99") Integer qty,
			@Size(max = 140, message = "Note must be at most 140 characters") String note) {
	}

	public record UpdateLineRequest(
			@NotNull @Min(value = 1, message = "Quantity must be 1 to 99") @Max(value = 99,
					message = "Quantity must be 1 to 99") Integer qty,
			@Size(max = 140, message = "Note must be at most 140 characters") String note,
			@NotNull(message = "The line version is required") Integer version) {
	}

	public record CancelLineRequest(
			@NotBlank(message = "A reason is required") @Size(max = 140,
					message = "Reason must be at most 140 characters") String reason) {
	}

	@GetMapping
	@Operation(summary = "All open orders with table, waiter, total, and age")
	public ResponseEntity<List<OpenOrderView>> openOrders() {
		Instant now = clock.instant();
		var views = service.openOrders()
			.stream()
			.map(o -> new OpenOrderView(o.id(), o.tableLabel(), o.waiterName(), Money.format(o.totalRappen()),
					o.openedAt(), Duration.between(o.openedAt(), now).toMinutes(), o.unsentLines()))
			.toList();
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(views);
	}

	@PostMapping
	@Operation(summary = "Open an order for a table",
			description = "Returns the table's open order (200) if it has one, otherwise creates it (201). "
					+ "The table label is 1 to 10 letters or digits and stored upper-case.")
	public ResponseEntity<OrderView> open(@Valid @RequestBody OpenRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		var result = service.open(r.tableLabel(), current);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(OrderView.of(result.detail()));
	}

	@GetMapping("/{id}")
	@Operation(summary = "An order with its lines and rounds")
	public ResponseEntity<OrderView> order(@PathVariable long id) {
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(OrderView.of(service.detail(id)));
	}

	@PostMapping("/{id}/lines")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add an unsent line",
			description = "Name, type, category, and price are copied from the menu item. 409 with "
					+ "menuItemInactive=true if the item was deactivated.")
	public OrderView addLine(@PathVariable long id, @Valid @RequestBody AddLineRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		return OrderView.of(service.addLine(id, r.menuItemId(), r.qty(), r.note(), current));
	}

	@PutMapping("/{id}/lines/{lineId}")
	@Operation(summary = "Change quantity or note of an unsent line",
			description = "version is the line version the client last saw; 409 if the line changed meanwhile.")
	public OrderView updateLine(@PathVariable long id, @PathVariable long lineId,
			@Valid @RequestBody UpdateLineRequest r) {
		return OrderView.of(service.updateLine(id, lineId, r.qty(), r.note(), r.version()));
	}

	@DeleteMapping("/{id}/lines/{lineId}")
	@Operation(summary = "Remove an unsent line")
	public OrderView removeLine(@PathVariable long id, @PathVariable long lineId) {
		return OrderView.of(service.removeLine(id, lineId));
	}

	@PostMapping("/{id}/send")
	@Operation(summary = "Send all unsent lines as one round",
			description = "Creates the print jobs in the same transaction. Does nothing (round = null) if there are "
					+ "no unsent lines.")
	public SendView send(@PathVariable long id, @AuthenticationPrincipal AuthenticatedStaff current) {
		var result = service.send(id, current);
		return new SendView(result.round() == null ? null : RoundView.of(result.round()),
				OrderView.of(result.detail()));
	}

	@PostMapping("/{id}/lines/{lineId}/cancel")
	@Operation(summary = "Cancel a sent line", description = "Prints a cancellation ticket. A reason is required.")
	public OrderView cancelLine(@PathVariable long id, @PathVariable long lineId,
			@Valid @RequestBody CancelLineRequest r, @AuthenticationPrincipal AuthenticatedStaff current) {
		return OrderView.of(service.cancelLine(id, lineId, r.reason(), current));
	}

	@Bean
	static FilterRegistrationBean<ShallowEtagHeaderFilter> ordersEtagFilter() {
		var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/orders", "/orders/*");
		return registration;
	}

}
