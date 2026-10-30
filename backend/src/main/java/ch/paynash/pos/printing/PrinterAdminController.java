package ch.paynash.pos.printing;

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

import ch.paynash.pos.menu.ItemType;
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

	public PrinterAdminController(PrinterService service) {
		this.service = service;
	}

	public record PrinterView(long id, String name, String address, String deviceId, ItemType type, boolean active) {

		static PrinterView of(Printer p) {
			return new PrinterView(p.id(), p.name(), p.address(), p.deviceId(), p.itemType(), p.active());
		}

	}

	public record PrinterRequest(@NotBlank @Size(max = 60) String name,
			@NotBlank @Size(max = 255) @Pattern(regexp = "\\s*(https?://)?[A-Za-z0-9.\\-\\[\\]:]+/?\\s*",
					message = "Enter an IP address or host name, optionally with port") String address,
			@Size(max = 60) String deviceId, @NotNull(message = "Type is required (food or drink)") ItemType type,
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
		return service.list().stream().map(PrinterView::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a printer", description = "At most one active printer per type (food or drink).")
	public PrinterView create(@Valid @RequestBody PrinterRequest r) {
		return PrinterView.of(service.create(r.toInput()));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit, deactivate, or reactivate a printer",
			description = "Tickets already queued for this printer are still sent to it.")
	public PrinterView update(@PathVariable long id, @Valid @RequestBody PrinterRequest r) {
		return PrinterView.of(service.update(id, r.toInput()));
	}

	@PostMapping("/{id}/test")
	@Operation(summary = "Print a test ticket now and report whether it worked")
	public TestPrintView testPrint(@PathVariable long id) {
		return switch (service.testPrint(id)) {
			case EposClient.Result.Printed p -> new TestPrintView(true, null);
			case EposClient.Result.Failed f -> new TestPrintView(false, f.error());
		};
	}

}
