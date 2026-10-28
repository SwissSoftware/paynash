package ch.paynash.pos.order;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.order.OrderController.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/admin/orders")
@Tag(name = "Admin: orders")
public class OrderAdminController {

	private final OrderService service;

	public OrderAdminController(OrderService service) {
		this.service = service;
	}

	public record VoidRequest(
			@NotBlank(message = "A reason is required") @Size(max = 140,
					message = "Reason must be at most 140 characters") String reason,
			Integer version) {
	}

	@PostMapping("/{id}/void")
	@Operation(summary = "Void an open order",
			description = "A reason is required. If version is given and the order changed meanwhile, answers 409.")
	public OrderView voidOrder(@PathVariable long id, @Valid @RequestBody VoidRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		return OrderView.of(service.voidOrder(id, r.reason(), r.version(), current));
	}

}
