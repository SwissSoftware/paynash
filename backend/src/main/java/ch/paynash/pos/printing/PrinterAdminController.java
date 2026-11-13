package ch.paynash.pos.printing;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/admin/printers")
@Tag(name = "Admin: printers")
public class PrinterAdminController {

	private final PrinterService service;

	private final PrinterStatusMonitor monitor;

	public PrinterAdminController(PrinterService service, PrinterStatusMonitor monitor) {
		this.service = service;
		this.monitor = monitor;
	}

	/**
	 * {@code status} is the printer's last known state and {@code statusCheckedAt} when it was seen.
	 * {@code stations} are the active stations showing the printer, each with the time of its last poll.
	 */
	public record PrinterView(long id, String name, String address, String deviceId, PrintPurpose type, boolean active,
			PrinterState status, Instant statusCheckedAt, List<PrinterRepository.StationSeen> stations) {

	}

	private PrinterView view(Printer p) {
		PrinterStatusMonitor.Status s = monitor.status(p.id());
		return new PrinterView(p.id(), p.name(), p.address(), p.deviceId(), p.purpose(), p.active(), s.state(),
				s.checkedAt(), service.stationsShowing(p.id()));
	}

	public record PrinterRequest(@NotBlank @Size(max = 60) String name,
			@NotBlank @Size(max = 255) @Pattern(regexp = "\\s*(https?://)?[A-Za-z0-9.\\-\\[\\]:]+/?\\s*",
					message = "Enter an IP address or host name, optionally with port") String address,
			@Size(max = 60) String deviceId, @NotNull(message = "Type is required (food, drink, or receipt)") PrintPurpose type,
			Boolean active) {

		PrinterService.PrinterInput toInput() {
			return new PrinterService.PrinterInput(name, address, deviceId, type, active == null || active);
		}

	}

	public record TestPrintView(boolean success, String error) {
	}

	@GetMapping
	@Operation(summary = "List all printers")
	public List<PrinterView> list() {
		return service.list().stream().map(this::view).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a printer", description = "At most one active printer per type (food or drink).")
	public PrinterView create(@Valid @RequestBody PrinterRequest r) {
		return view(service.create(r.toInput()));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit, deactivate, or reactivate a printer",
			description = "Tickets already queued for this printer are still sent to it.")
	public PrinterView update(@PathVariable long id, @Valid @RequestBody PrinterRequest r) {
		return view(service.update(id, r.toInput()));
	}

	@PostMapping("/{id}/test")
	@Operation(summary = "Print a test ticket now and report whether it worked")
	public TestPrintView testPrint(@PathVariable long id) {
		EposClient.Result result = service.testPrint(id);
		return new TestPrintView(result instanceof EposClient.Result.Printed, result.error());
	}

}
