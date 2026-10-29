package ch.paynash.pos.table;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/admin/tables")
@Tag(name = "Admin: tables")
public class TableAdminController {

	private final TableService service;

	public TableAdminController(TableService service) {
		this.service = service;
	}

	public record AdminTableView(long id, String number, String name, boolean active) {

		static AdminTableView of(DiningTable t) {
			return new AdminTableView(t.id(), t.number(), t.name(), t.active());
		}

	}

	/** {@code number} is validated and upper-cased by the service, the same way as order table labels. */
	public record CreateTableRequest(String number, @Size(max = 60) String name, Boolean active) {
	}

	public record UpdateTableRequest(String number, @Size(max = 60) String name, @NotNull Boolean active) {
	}

	@GetMapping
	@Operation(summary = "List all tables, including inactive ones")
	public List<AdminTableView> list() {
		return service.all().stream().map(AdminTableView::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a table")
	public AdminTableView create(@Valid @RequestBody CreateTableRequest r) {
		return AdminTableView.of(service.create(r.number(), r.name(), r.active() == null || r.active()));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit, deactivate, or reactivate a table",
			description = "Orders already sent keep the number they were sent with.")
	public AdminTableView update(@PathVariable long id, @Valid @RequestBody UpdateTableRequest r) {
		return AdminTableView.of(service.update(id, r.number(), r.name(), r.active()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a table", description = "Orders already sent keep their table number.")
	public void delete(@PathVariable long id) {
		service.delete(id);
	}

}
