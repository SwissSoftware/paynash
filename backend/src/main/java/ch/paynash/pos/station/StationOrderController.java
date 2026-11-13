package ch.paynash.pos.station;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.auth.AuthService;
import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.order.OrderController.OrderView;
import ch.paynash.pos.order.OrderService;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.RequiresMode;
import ch.paynash.pos.staff.Staff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cancelling lines and voiding orders at a station (spec order-taking, "PIN confirmation for station cancellations";
 * design D7). The station stays logged in; each cancellation is confirmed with the username and PIN of an active
 * admin or waiter, who is recorded together with the station. The PIN is checked in its own transaction first, so a
 * wrong PIN counts toward the lockout even though nothing is cancelled.
 */
@RestController
@RequiresMode(Mode.EVENT)
@RequestMapping("/station/orders")
@Tag(name = "Station")
public class StationOrderController {

	private final AuthService auth;

	private final OrderService orders;

	public StationOrderController(AuthService auth, OrderService orders) {
		this.auth = auth;
		this.orders = orders;
	}

	public record ConfirmedCancelRequest(@NotBlank(message = "Username is required") String username,
			@NotNull(message = "PIN is required") String pin,
			@NotBlank(message = "A reason is required") @Size(max = 140,
					message = "Reason must be at most 140 characters") String reason) {
	}

	public record ConfirmedVoidRequest(@NotBlank(message = "Username is required") String username,
			@NotNull(message = "PIN is required") String pin,
			@NotBlank(message = "A reason is required") @Size(max = 140,
					message = "Reason must be at most 140 characters") String reason,
			Integer version) {
	}

	@GetMapping("/{id}")
	@Operation(summary = "An order with its lines and tickets, to cancel lines or void it from the station")
	public OrderView order(@PathVariable long id) {
		return OrderView.of(orders.detail(id));
	}

	@PostMapping("/{id}/lines/{lineId}/cancel")
	@Operation(summary = "Cancel a line of a sent order, confirmed with a staff PIN",
			description = "Prints a cancellation ticket. A wrong username or PIN answers 400 with an error on pin "
					+ "and changes nothing. Accepts an Idempotency-Key.")
	public OrderView cancelLine(@PathVariable long id, @PathVariable long lineId,
			@Valid @RequestBody ConfirmedCancelRequest r, @AuthenticationPrincipal AuthenticatedStaff station) {
		Staff confirmedBy = auth.confirm(r.username(), r.pin());
		return OrderView.of(orders.cancelLine(id, lineId, r.reason(), confirmedBy.id(), station.id()));
	}

	@PostMapping("/{id}/void")
	@Operation(summary = "Void a sent order, confirmed with a staff PIN",
			description = "Cancels all remaining lines and prints cancellation tickets. A wrong username or PIN "
					+ "answers 400 with an error on pin and changes nothing. If version is given and the order "
					+ "changed meanwhile, answers 409. Accepts an Idempotency-Key.")
	public OrderView voidOrder(@PathVariable long id, @Valid @RequestBody ConfirmedVoidRequest r,
			@AuthenticationPrincipal AuthenticatedStaff station) {
		Staff confirmedBy = auth.confirm(r.username(), r.pin());
		return OrderView.of(orders.voidOrder(id, r.reason(), r.version(), confirmedBy.id(), station.id()));
	}

}
