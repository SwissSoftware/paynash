package ch.paynash.pos.station;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
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
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintJobs;
import ch.paynash.pos.printing.PrinterState;
import ch.paynash.pos.printing.Ticket;
import ch.paynash.pos.printing.TicketView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The print station's API (spec print-station). Only for the station role. Clients poll with {@code If-None-Match}
 * (design D8).
 */
@RestController
@RequestMapping("/station")
@Tag(name = "Station")
public class StationController {

	private final StationService service;

	private final PrintJobs printJobs;

	public StationController(StationService service, PrintJobs printJobs) {
		this.service = service;
		this.printJobs = printJobs;
	}

	/**
	 * An assigned printer with its last known state and queue: the last printed ticket number, how many tickets wait,
	 * since when the oldest waits, and how many are unclear.
	 */
	public record StationPrinterView(long id, String name, ItemType type, boolean active, PrinterState status,
			Instant statusCheckedAt, Integer lastPrintedTicketNo, String lastPrintedLabel, int waiting,
			Instant oldestWaitingSince, Long oldestWaitingSeconds, int unclear) {

		static StationPrinterView of(StationService.StationPrinter p) {
			var q = p.queue();
			return new StationPrinterView(p.printer().id(), p.printer().name(), p.printer().type(),
					p.printer().active(), p.status().state(), p.status().checkedAt(), q.lastPrintedTicketNo(),
					q.lastPrintedTicketNo() == null ? null : Ticket.label(p.printer().name(), q.lastPrintedTicketNo()),
					q.waiting(), q.oldestWaitingSince(), p.oldestWaitingSeconds(), q.unclear());
		}

	}

	/**
	 * A ticket in the station list. {@code status} is pending, printing, printed, unclear, or failed;
	 * {@code needsResolution} marks an unclear ticket still waiting for "was printed" or "reprint".
	 */
	public record StationTicketView(long id, long orderId, Integer ticketNo, String label, String kind,
			String status, String waitingReason, String error, String tableLabel, String waiter, int lines,
			String resolution, boolean reprinted, boolean needsResolution, Instant createdAt) {

		static StationTicketView of(StationRepository.StationTicket t) {
			return new StationTicketView(t.id(), t.orderId(), t.ticketNo(),
					t.ticketNo() == null ? null : Ticket.label(t.printerName(), t.ticketNo()), t.kind(), t.status(),
					t.waitingReason(), t.error(), t.tableLabel(), t.waiterName(), t.lineCount(), t.resolution(),
					t.reprinted(), "unclear".equals(t.status()) && t.resolution() == null, t.createdAt());
		}

	}

	@GetMapping("/printers")
	@Operation(summary = "The printers this station shows, with status and queue")
	public ResponseEntity<List<StationPrinterView>> printers(@AuthenticationPrincipal AuthenticatedStaff station) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.body(service.printers(station).stream().map(StationPrinterView::of).toList());
	}

	@GetMapping("/tickets")
	@Operation(summary = "Tickets of an assigned printer",
			description = "Today's tickets (Europe/Zurich) and every older ticket that still needs attention. "
					+ "Unclear tickets that need a decision come first, then newest first. 403 for a printer the "
					+ "station does not show.")
	public ResponseEntity<List<StationTicketView>> tickets(@RequestParam long printerId,
			@AuthenticationPrincipal AuthenticatedStaff station) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.body(service.tickets(station, printerId).stream().map(StationTicketView::of).toList());
	}

	public record ResolveRequest(@NotNull(message = "Choose printed or reprint") PrintJob.Resolution resolution) {
	}

	@PostMapping("/tickets/{id}/resolve")
	@Operation(summary = "Resolve an unclear ticket",
			description = "printed: the ticket was found in the pile; nothing is printed. reprint: it is printed again "
					+ "marked REPRINT and the answer is the reprint. 409 if the ticket is not unclear or was already "
					+ "resolved. Accepts an Idempotency-Key.")
	public TicketView resolve(@PathVariable long id, @Valid @RequestBody ResolveRequest r,
			@AuthenticationPrincipal AuthenticatedStaff station) {
		return TicketView.of(printJobs.resolve(id, r.resolution(), station.id(), p -> service.shows(station, p)));
	}

	@PostMapping("/tickets/{id}/reprint")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Reprint a ticket of an assigned printer",
			description = "Same as the waiters' reprint. 409 while the ticket is waiting or printing. Accepts an "
					+ "Idempotency-Key.")
	public TicketView reprint(@PathVariable long id, @AuthenticationPrincipal AuthenticatedStaff station) {
		return TicketView.of(printJobs.reprintAtStation(id, station.id(), p -> service.shows(station, p)));
	}

	@Bean
	static FilterRegistrationBean<ShallowEtagHeaderFilter> stationEtagFilter() {
		var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/station/printers", "/station/tickets");
		return registration;
	}

}
