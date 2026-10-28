package ch.paynash.pos.staff;

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
@RequestMapping("/admin/staff")
@Tag(name = "Admin: staff")
public class StaffAdminController {

	private static final String PIN_REGEX = "\\d{4,8}";

	private static final String PIN_MESSAGE = "PIN must be 4 to 8 digits";

	private final StaffService service;

	public StaffAdminController(StaffService service) {
		this.service = service;
	}

	public record StaffView(long id, String username, String displayName, Role role, boolean active) {

		static StaffView of(Staff s) {
			return new StaffView(s.id(), s.username(), s.displayName(), s.role(), s.active());
		}

	}

	public record CreateStaffRequest(
			@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+",
					message = "Only letters, digits, '.', '_' and '-'") String username,
			@NotBlank @Size(max = 80) String displayName, @NotNull Role role,
			@NotNull @Pattern(regexp = PIN_REGEX, message = PIN_MESSAGE) String pin) {
	}

	public record UpdateStaffRequest(@NotBlank @Size(max = 80) String displayName, @NotNull Role role,
			@NotNull Boolean active) {
	}

	public record ResetPinRequest(@NotNull @Pattern(regexp = PIN_REGEX, message = PIN_MESSAGE) String pin) {
	}

	@GetMapping
	@Operation(summary = "List all staff accounts")
	public List<StaffView> list() {
		return service.list().stream().map(StaffView::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a staff account")
	public StaffView create(@Valid @RequestBody CreateStaffRequest r) {
		return StaffView.of(service.create(r.username(), r.displayName(), r.role(), r.pin()));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit a staff account", description = "Setting active=false ends all its sessions.")
	public StaffView update(@PathVariable long id, @Valid @RequestBody UpdateStaffRequest r) {
		return StaffView.of(service.update(id, r.displayName(), r.role(), r.active()));
	}

	@PostMapping("/{id}/pin")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Reset the PIN of a staff account", description = "Also clears a login lockout.")
	public void resetPin(@PathVariable long id, @Valid @RequestBody ResetPinRequest r) {
		service.resetPin(id, r.pin());
	}

}
