package ch.paynash.pos.table;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The tables a waiter can pick when taking an order. */
@RestController
@Tag(name = "Tables")
public class TableController {

	private final TableService service;

	public TableController(TableService service) {
		this.service = service;
	}

	public record TableView(long id, String number, String name) {
	}

	@GetMapping("/tables")
	@Operation(summary = "Active tables, sorted by number (\"2\" before \"10\")")
	public List<TableView> list() {
		return service.active().stream().map(t -> new TableView(t.id(), t.number(), t.name())).toList();
	}

}
