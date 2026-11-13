package ch.paynash.pos.customer;

import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.common.Money;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.RequiresMode;
import ch.paynash.pos.staff.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Salon customers (spec customer-management). Deactivating, deleting, and exporting are admin-only. */
@RestController
@RequiresMode(Mode.SALON)
@Tag(name = "Customers")
public class CustomerController {

	private final CustomerService service;

	public CustomerController(CustomerService service) {
		this.service = service;
	}

	/** {@code name} is "Deleted customer" for anonymized customers. */
	public record CustomerView(long id, int number, String name, String firstName, String lastName, String phone,
			String email, String street, String postalCode, String city, String note, boolean active,
			boolean anonymized) {

		static CustomerView of(Customer c) {
			return new CustomerView(c.id(), c.number(), c.displayName(), c.firstName(), c.lastName(), c.phone(),
					c.email(), c.street(), c.postalCode(), c.city(), c.note(), c.active(), c.anonymized());
		}

	}

	public record CustomerRequest(String firstName, String lastName, String phone, String email, String street,
			String postalCode, String city, String note) {

		CustomerService.Input toInput() {
			return new CustomerService.Input(firstName, lastName, phone, email, street, postalCode, city, note);
		}

	}

	public record InvoiceSummaryView(long id, String number, String date, String total, String status) {
	}

	/** A customer with their invoices, newest first, and the total of the invoices that are not cancelled. */
	public record CustomerDetailView(CustomerView customer, List<InvoiceSummaryView> invoices, String revenue) {
	}

	public record DeleteResultView(CustomerService.DeleteResult result) {
	}

	@GetMapping("/customers")
	@Operation(summary = "Search customers",
			description = "q matches name, email, and customer number ignoring case, and phone numbers on their digits. "
					+ "Lists at most 50 customers by last and first name. includeInactive is for admins only.")
	public List<CustomerView> search(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean includeInactive,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		if (includeInactive && current.role() != Role.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can list inactive customers");
		}
		return service.search(q, includeInactive).stream().map(CustomerView::of).toList();
	}

	@GetMapping("/customers/duplicates")
	@Operation(summary = "Possible duplicates of a customer about to be created or edited",
			description = "Customers with the same first and last name, the same phone digits, or the same email. "
					+ "Only a warning: creating anyway is allowed.")
	public List<CustomerView> duplicates(@RequestParam(required = false) String firstName,
			@RequestParam(required = false) String lastName, @RequestParam(required = false) String phone,
			@RequestParam(required = false) String email, @RequestParam(required = false) Long exceptId) {
		return service
			.possibleDuplicates(new CustomerService.Input(firstName, lastName, phone, email, null, null, null, null),
					exceptId)
			.stream()
			.map(CustomerView::of)
			.toList();
	}

	@GetMapping("/customers/{id}")
	@Operation(summary = "A customer with their invoice history")
	public CustomerDetailView get(@PathVariable long id) {
		Customer c = service.get(id);
		var invoices = service.invoices(id);
		long revenue = invoices.stream()
			.filter(i -> !"cancelled".equals(i.status()))
			.mapToLong(CustomerRepository.InvoiceSummary::totalRappen)
			.sum();
		return new CustomerDetailView(CustomerView.of(c),
				invoices.stream()
					.map(i -> new InvoiceSummaryView(i.id(), i.number(), i.invoiceDate().toString(),
							Money.format(i.totalRappen()), i.status()))
					.toList(),
				Money.format(revenue));
	}

	@PostMapping("/customers")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a customer", description = "First name or last name is required.")
	public CustomerView create(@RequestBody CustomerRequest r) {
		return CustomerView.of(service.create(r.toInput()));
	}

	@PutMapping("/customers/{id}")
	@Operation(summary = "Edit a customer", description = "Issued invoices keep the details they were issued with.")
	public CustomerView update(@PathVariable long id, @RequestBody CustomerRequest r) {
		return CustomerView.of(service.update(id, r.toInput()));
	}

	@PostMapping("/admin/customers/{id}/deactivate")
	@Operation(summary = "Deactivate a customer", description = "Inactive customers cannot be picked for new orders.")
	public CustomerView deactivate(@PathVariable long id) {
		return CustomerView.of(service.setActive(id, false));
	}

	@PostMapping("/admin/customers/{id}/reactivate")
	@Operation(summary = "Reactivate a customer")
	public CustomerView reactivate(@PathVariable long id) {
		return CustomerView.of(service.setActive(id, true));
	}

	@DeleteMapping("/admin/customers/{id}")
	@Operation(summary = "Delete or anonymize a customer",
			description = "A customer without orders is deleted. A customer with orders is anonymized: personal data "
					+ "is removed, the number stays for reports, and issued invoices keep their copy.")
	public DeleteResultView delete(@PathVariable long id) {
		return new DeleteResultView(service.delete(id));
	}

	@GetMapping("/admin/customers/{id}/export")
	@Operation(summary = "Export all data stored about a customer as JSON (access request)")
	public ResponseEntity<Map<String, Object>> export(@PathVariable long id) {
		Customer c = service.get(id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("Kunde-" + c.number() + ".json").build().toString())
			.body(service.export(id));
	}

}
