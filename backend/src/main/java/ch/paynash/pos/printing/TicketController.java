package ch.paynash.pos.printing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reprinting tickets (spec ticket-printing, "Reprint"); the tickets themselves come with {@code GET /orders/{id}}. */
@RestController
@Tag(name = "Orders")
public class TicketController {

	private final PrintJobs printJobs;

	public TicketController(PrintJobs printJobs) {
		this.printJobs = printJobs;
	}

	@PostMapping("/orders/{orderId}/tickets/{ticketId}/reprint")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Reprint a ticket",
			description = "Queues the same content marked REPRINT for the printer that currently serves the ticket's "
					+ "type. Accepts an Idempotency-Key, so a retried request prints once. 409 while the ticket is "
					+ "still pending.")
	public TicketView reprint(@PathVariable long orderId, @PathVariable long ticketId) {
		return TicketView.of(printJobs.reprint(orderId, ticketId));
	}

}
