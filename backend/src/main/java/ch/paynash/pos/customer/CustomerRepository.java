package ch.paynash.pos.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

	private static final String COLUMNS = """
			id, number, first_name, last_name, phone, email, street, postal_code, city, note, active,
			anonymized_at IS NOT NULL AS anonymized""";

	private static final String ORDER_BY = """
			 ORDER BY lower(coalesce(last_name, '')), lower(coalesce(first_name, '')), number""";

	private final JdbcClient jdbc;

	public CustomerRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record Fields(String firstName, String lastName, String phone, String email, String street,
			String postalCode, String city, String note) {
	}

	public Optional<Customer> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM customer WHERE id = ?").param(id).query(this::map).optional();
	}

	/**
	 * Case-insensitive search over name, email, and customer number; phone numbers match on their digits only. An
	 * empty query lists everyone.
	 */
	public List<Customer> search(String query, boolean includeInactive, int limit) {
		var where = new ArrayList<String>();
		var params = new ArrayList<Object>();
		if (!includeInactive) {
			where.add("active");
		}
		if (query != null && !query.isBlank()) {
			String q = "%" + escapeLike(query.trim().toLowerCase()) + "%";
			var any = new ArrayList<String>();
			any.add("lower(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) LIKE ?");
			any.add("lower(coalesce(last_name, '') || ' ' || coalesce(first_name, '')) LIKE ?");
			any.add("lower(email) LIKE ?");
			params.add(q);
			params.add(q);
			params.add(q);
			String trimmed = query.trim();
			if (trimmed.matches("\\d{1,9}")) {
				any.add("number = ?");
				params.add(Integer.parseInt(trimmed));
			}
			String digits = trimmed.replaceAll("\\D", "");
			if (digits.length() >= 3) {
				any.add("phone_digits LIKE ?");
				params.add("%" + digits + "%");
			}
			where.add("(" + String.join(" OR ", any) + ")");
		}
		String sql = "SELECT " + COLUMNS + " FROM customer" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
				+ ORDER_BY + " LIMIT " + limit;
		return jdbc.sql(sql).params(params).query(this::map).list();
	}

	/** Customers that are not anonymized and share the full name, the phone digits, or the email. */
	public List<Customer> findPossibleDuplicates(String firstName, String lastName, String phoneDigits, String email,
			Long exceptId) {
		return jdbc.sql("SELECT " + COLUMNS + """
				 FROM customer
				WHERE anonymized_at IS NULL AND id <> ?
				  AND ((? AND lower(coalesce(first_name, '')) = lower(?) AND lower(coalesce(last_name, '')) = lower(?))
				       OR (? <> '' AND phone_digits = ?)
				       OR (? <> '' AND lower(email) = lower(?)))""" + ORDER_BY)
			.params(exceptId == null ? -1L : exceptId, firstName != null || lastName != null,
					firstName == null ? "" : firstName, lastName == null ? "" : lastName,
					phoneDigits == null ? "" : phoneDigits, phoneDigits == null ? "" : phoneDigits,
					email == null ? "" : email, email == null ? "" : email)
			.query(this::map)
			.list();
	}

	public long insert(Fields f, Instant now) {
		return jdbc.sql("""
				INSERT INTO customer (first_name, last_name, phone, phone_digits, email, street, postal_code, city, note,
				                      created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
			.params(f.firstName(), f.lastName(), f.phone(), digits(f.phone()), f.email(), f.street(), f.postalCode(),
					f.city(), f.note(), Timestamp.from(now), Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	public void update(long id, Fields f, Instant now) {
		jdbc.sql("""
				UPDATE customer SET first_name = ?, last_name = ?, phone = ?, phone_digits = ?, email = ?, street = ?,
				                    postal_code = ?, city = ?, note = ?, updated_at = ?
				WHERE id = ?""")
			.params(f.firstName(), f.lastName(), f.phone(), digits(f.phone()), f.email(), f.street(), f.postalCode(),
					f.city(), f.note(), Timestamp.from(now), id)
			.update();
	}

	public void setActive(long id, boolean active, Instant now) {
		jdbc.sql("UPDATE customer SET active = ?, updated_at = ? WHERE id = ?")
			.params(active, Timestamp.from(now), id)
			.update();
	}

	/** Locks the customer row, so a delete and a new order for the same customer cannot overlap. */
	public Optional<Customer> lock(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM customer WHERE id = ? FOR UPDATE")
			.param(id)
			.query(this::map)
			.optional();
	}

	/**
	 * Locks the customer for a new order (shared), so the customer cannot be deleted or deactivated while the order
	 * is being saved.
	 */
	public Optional<Customer> lockForOrder(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM customer WHERE id = ? FOR SHARE")
			.param(id)
			.query(this::map)
			.optional();
	}

	public boolean hasOrders(long id) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM orders WHERE customer_id = ?)").param(id).query(Boolean.class).single();
	}

	public void delete(long id) {
		jdbc.sql("DELETE FROM customer WHERE id = ?").param(id).update();
	}

	/** Removes all personal data and keeps the number for reports (spec customer-management). */
	public void anonymize(long id, Instant now) {
		jdbc.sql("""
				UPDATE customer SET first_name = NULL, last_name = NULL, phone = NULL, phone_digits = NULL, email = NULL,
				                    street = NULL, postal_code = NULL, city = NULL, note = NULL, active = FALSE,
				                    anonymized_at = ?, updated_at = ?
				WHERE id = ?""")
			.params(Timestamp.from(now), Timestamp.from(now), id)
			.update();
	}

	/** One line of the customer history; {@code status} is open, paid, or cancelled. */
	public record InvoiceSummary(long id, String number, LocalDate invoiceDate, long totalRappen, String status) {
	}

	public List<InvoiceSummary> invoices(long customerId) {
		return jdbc.sql("""
				SELECT i.id, i.number, i.invoice_date, i.total_rappen,
				       CASE WHEN i.status = 'cancelled' THEN 'cancelled'
				            WHEN EXISTS (SELECT 1 FROM invoice_payment p WHERE p.invoice_id = i.id AND p.removed_at IS NULL)
				                 THEN 'paid'
				            ELSE 'open' END AS status
				  FROM invoice i
				 WHERE i.customer_id = ?
				 ORDER BY i.invoice_date DESC, i.year DESC, i.seq DESC""")
			.param(customerId)
			.query((rs, n) -> new InvoiceSummary(rs.getLong("id"), rs.getString("number"),
					rs.getObject("invoice_date", LocalDate.class), rs.getLong("total_rappen"), rs.getString("status")))
			.list();
	}

	static String digits(String phone) {
		if (phone == null) {
			return null;
		}
		String d = phone.replaceAll("\\D", "");
		return d.isEmpty() ? null : d;
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private Customer map(ResultSet rs, int row) throws SQLException {
		return new Customer(rs.getLong("id"), rs.getInt("number"), rs.getString("first_name"),
				rs.getString("last_name"), rs.getString("phone"), rs.getString("email"), rs.getString("street"),
				rs.getString("postal_code"), rs.getString("city"), rs.getString("note"), rs.getBoolean("active"),
				rs.getBoolean("anonymized"));
	}

}
