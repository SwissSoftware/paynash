package ch.paynash.pos.customer;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;

/** Salon customers (spec customer-management, design D4). */
@Service
public class CustomerService {

	static final int SEARCH_LIMIT = 50;

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final CustomerRepository customers;

	private final CustomerExport export;

	private final Clock clock;

	public CustomerService(CustomerRepository customers, CustomerExport export, Clock clock) {
		this.customers = customers;
		this.export = export;
		this.clock = clock;
	}

	/** The input of create and edit; blank strings count as not set. */
	public record Input(String firstName, String lastName, String phone, String email, String street,
			String postalCode, String city, String note) {
	}

	public List<Customer> search(String query, boolean includeInactive) {
		return customers.search(query, includeInactive, SEARCH_LIMIT);
	}

	public Customer get(long id) {
		return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
	}

	public List<Customer> possibleDuplicates(Input in, Long exceptId) {
		var f = fields(in, false);
		return customers.findPossibleDuplicates(f.firstName(), f.lastName(), CustomerRepository.digits(f.phone()),
				f.email(), exceptId);
	}

	@Transactional
	public Customer create(Input in) {
		return get(customers.insert(fields(in, true), clock.instant()));
	}

	@Transactional
	public Customer update(long id, Input in) {
		Customer current = lockExisting(id);
		if (current.anonymized()) {
			throw ApiException.conflict("This customer was deleted and cannot be edited");
		}
		customers.update(id, fields(in, true), clock.instant());
		return get(id);
	}

	@Transactional
	public Customer setActive(long id, boolean active) {
		Customer current = lockExisting(id);
		if (current.anonymized() && active) {
			throw ApiException.conflict("This customer was deleted and cannot be reactivated");
		}
		customers.setActive(id, active, clock.instant());
		return get(id);
	}

	/** What {@link #delete} did. */
	public enum DeleteResult {

		DELETED, ANONYMIZED;

		@com.fasterxml.jackson.annotation.JsonValue
		public String json() {
			return name().toLowerCase();
		}

	}

	/**
	 * Deletes a customer without orders completely, and anonymizes one with orders. Issued invoices keep their own
	 * copy of the customer details for the retention period (spec customer-management, "Delete or anonymize").
	 */
	@Transactional
	public DeleteResult delete(long id) {
		lockExisting(id);
		if (customers.hasOrders(id)) {
			customers.anonymize(id, clock.instant());
			return DeleteResult.ANONYMIZED;
		}
		customers.delete(id);
		return DeleteResult.DELETED;
	}

	public List<CustomerRepository.InvoiceSummary> invoices(long id) {
		get(id);
		return customers.invoices(id);
	}

	/** Everything stored about one customer, for an access request. */
	public Map<String, Object> export(long id) {
		return export.collect(get(id));
	}

	private Customer lockExisting(long id) {
		return customers.lock(id).orElseThrow(() -> ApiException.notFound("Customer"));
	}

	private static CustomerRepository.Fields fields(Input in, boolean validate) {
		Map<String, String> errors = new LinkedHashMap<>();
		String first = text(in.firstName(), "firstName", 60, errors);
		String last = text(in.lastName(), "lastName", 60, errors);
		String phone = text(in.phone(), "phone", 30, errors);
		String email = text(in.email(), "email", 120, errors);
		String street = text(in.street(), "street", 80, errors);
		String postalCode = text(in.postalCode(), "postalCode", 10, errors);
		String city = text(in.city(), "city", 60, errors);
		String note = text(in.note(), "note", 500, errors);
		if (first == null && last == null) {
			errors.putIfAbsent("lastName", "First name or last name is required");
		}
		if (email != null && !EMAIL.matcher(email).matches()) {
			errors.putIfAbsent("email", "Not a valid email address");
		}
		if (validate && !errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
		}
		return new CustomerRepository.Fields(first, last, phone, email, street, postalCode, city, note);
	}

	private static String text(String value, String field, int max, Map<String, String> errors) {
		String v = value == null || value.isBlank() ? null : value.trim();
		if (v != null && v.length() > max) {
			errors.put(field, "At most " + max + " characters");
		}
		return v;
	}

}
