package ch.paynash.pos.printing;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.menu.ItemType;

/** Printer configuration (spec ticket-printing, "Printer configuration"). */
@Service
public class PrinterService {

	private final PrinterRepository printers;

	private final EposClient epos;

	private final TicketRenderer renderer;

	public PrinterService(PrinterRepository printers, EposClient epos, TicketRenderer renderer) {
		this.printers = printers;
		this.epos = epos;
		this.renderer = renderer;
	}

	public record PrinterInput(String name, String address, String deviceId, ItemType type, boolean active) {
	}

	public List<Printer> list() {
		return printers.findAll();
	}

	@Transactional
	public Printer create(PrinterInput in) {
		check(in, -1);
		try {
			return get(printers.insert(in.name().trim(), in.address().trim(), deviceId(in), in.type(), in.active()));
		}
		catch (DuplicateKeyException ex) {
			throw conflictOnSave(in, -1);
		}
	}

	@Transactional
	public Printer update(long id, PrinterInput in) {
		get(id);
		check(in, id);
		try {
			printers.update(id, in.name().trim(), in.address().trim(), deviceId(in), in.type(), in.active());
		}
		catch (DuplicateKeyException ex) {
			throw conflictOnSave(in, id);
		}
		return get(id);
	}

	/**
	 * Prints a test ticket right away, without the job queue, so the admin sees the result. Nothing is retried.
	 */
	public EposClient.Result testPrint(long id) {
		Printer printer = get(id);
		return epos.print(printer, renderer.testTicket(printer));
	}

	public Printer get(long id) {
		return printers.findById(id).orElseThrow(() -> ApiException.notFound("Printer"));
	}

	/** At most one active printer per type, so every line has exactly one destination. */
	private void check(PrinterInput in, long id) {
		if (printers.existsByName(in.name().trim(), id)) {
			throw ApiException.validation("name", "A printer with this name already exists");
		}
		if (in.active()) {
			printers.findOtherActiveFor(in.type(), id).ifPresent(other -> {
				throw ApiException.validation("type",
						"\"" + other.name() + "\" already prints " + in.type().dbValue() + ". Deactivate it first.");
			});
		}
	}

	/** Lost a race with another admin; report it the same way as the checks above. */
	private ApiException conflictOnSave(PrinterInput in, long id) {
		check(in, id);
		return ApiException.conflict("The printer configuration was changed meanwhile. Please try again.");
	}

	private static String deviceId(PrinterInput in) {
		return in.deviceId() == null || in.deviceId().isBlank() ? "local_printer" : in.deviceId().trim();
	}

}
