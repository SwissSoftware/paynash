package ch.paynash.pos.invoice;

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

import tools.jackson.databind.json.JsonMapper;

@Repository
public class InvoiceRepository {

	private static final String STATUS_SQL = """
			CASE WHEN i.status = 'cancelled' THEN 'cancelled'
			     WHEN EXISTS (SELECT 1 FROM invoice_payment p WHERE p.invoice_id = i.id AND p.removed_at IS NULL)
			          THEN 'paid'
			     ELSE 'open' END""";

	private static final String SELECT = """
			SELECT i.id, i.number, i.order_id, i.customer_id, i.invoice_date, i.issued_at, s.display_name AS issued_by_name,
			       i.business::text AS business, i.customer::text AS customer, i.vat_enabled, i.vat_uid, i.total_rappen,
			       %s AS status, i.cancelled_at, c.display_name AS cancelled_by_name, i.cancel_reason
			FROM invoice i
			JOIN staff s ON s.id = i.issued_by
			LEFT JOIN staff c ON c.id = i.cancelled_by""".formatted(STATUS_SQL);

	private final JdbcClient jdbc;

	private final JsonMapper json;

	public InvoiceRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	// --- numbering

	/**
	 * Takes the next number of {@code year}. The counter row stays locked until the transaction ends, and a rollback
	 * also rolls back the counter, so numbers have no gaps (add-salon-mode design D5).
	 */
	public int nextSeq(int year) {
		return jdbc.sql("""
				INSERT INTO invoice_counter (year, last_seq) VALUES (?, 1)
				ON CONFLICT (year) DO UPDATE SET last_seq = invoice_counter.last_seq + 1
				RETURNING last_seq""").param(year).query(Integer.class).single();
	}

	// --- writing

	public record NewInvoice(String number, int year, int seq, long orderId, long customerId, LocalDate invoiceDate,
			Instant issuedAt, long issuedBy, Invoice.Business business, Invoice.CustomerCopy customer,
			boolean vatEnabled, String vatUid, long totalRappen) {
	}

	public record NewLine(int position, long orderLineId, String name, String category, int qty, int unitPriceRappen,
			int vatRatePermille, long amountRappen, String note) {
	}

	public long insert(NewInvoice i, List<NewLine> lines, List<Vat.Row> vat) {
		long id = jdbc.sql("""
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, vat_uid, total_rappen)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?) RETURNING id""")
			.params(i.number(), i.year(), i.seq(), i.orderId(), i.customerId(), i.invoiceDate(),
					Timestamp.from(i.issuedAt()), i.issuedBy(), json.writeValueAsString(i.business()),
					json.writeValueAsString(i.customer()), i.vatEnabled(), i.vatUid(), i.totalRappen())
			.query(Long.class)
			.single();
		for (NewLine l : lines) {
			jdbc.sql("""
					INSERT INTO invoice_line (invoice_id, position, order_line_id, name, category, qty,
					                          unit_price_rappen, vat_rate_permille, amount_rappen, note)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
				.params(id, l.position(), l.orderLineId(), l.name(), l.category(), l.qty(), l.unitPriceRappen(),
						l.vatRatePermille(), l.amountRappen(), l.note())
				.update();
		}
		for (Vat.Row r : vat) {
			jdbc.sql("""
					INSERT INTO invoice_vat (invoice_id, vat_rate_permille, gross_rappen, net_rappen, vat_rappen)
					VALUES (?, ?, ?, ?, ?)""")
				.params(id, r.ratePermille(), r.grossRappen(), r.netRappen(), r.vatRappen())
				.update();
		}
		return id;
	}

	public void cancel(long id, long staffId, String reason, Instant now) {
		jdbc.sql("""
				UPDATE invoice SET status = 'cancelled', cancelled_at = ?, cancelled_by = ?, cancel_reason = ?
				WHERE id = ? AND status = 'issued'""")
			.params(Timestamp.from(now), staffId, reason, id)
			.update();
	}

	public void insertPayment(long invoiceId, String method, LocalDate paidOn, long staffId, Instant now) {
		jdbc.sql("""
				INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, ?)""")
			.params(invoiceId, method, paidOn, staffId, Timestamp.from(now))
			.update();
	}

	public void removePayment(long invoiceId, long staffId, String reason, Instant now) {
		jdbc.sql("""
				UPDATE invoice_payment SET removed_at = ?, removed_by = ?, remove_reason = ?
				WHERE invoice_id = ? AND removed_at IS NULL""")
			.params(Timestamp.from(now), staffId, reason, invoiceId)
			.update();
	}

	// --- reading

	/** The order's invoice that is not cancelled. */
	public Optional<Long> findActiveIdByOrder(long orderId) {
		return jdbc.sql("SELECT id FROM invoice WHERE order_id = ? AND status = 'issued'")
			.param(orderId)
			.query(Long.class)
			.optional();
	}

	public Optional<Invoice> find(long id) {
		return jdbc.sql(SELECT + " WHERE i.id = ?").param(id).query(this::map).optional().map(this::withDetails);
	}

	/** Locks the invoice row, so cancelling and payments on one invoice are serialised. */
	public Optional<Invoice> findForUpdate(long id) {
		return jdbc.sql(SELECT + " WHERE i.id = ? FOR UPDATE OF i")
			.param(id)
			.query(this::map)
			.optional()
			.map(this::withDetails);
	}

	/** One row of the invoice list. */
	public record Summary(long id, String number, LocalDate invoiceDate, int customerNumber, String customerName,
			long totalRappen, Invoice.Status status) {
	}

	/**
	 * Invoices dated in {@code [from, to]}, newest first; {@code status} filters (null for all), {@code query} matches
	 * the invoice number or the customer name as issued, ignoring case.
	 */
	public List<Summary> list(LocalDate from, LocalDate to, Invoice.Status status, String query) {
		var where = new ArrayList<String>(List.of("i.invoice_date BETWEEN ? AND ?"));
		var params = new ArrayList<Object>(List.of(from, to));
		if (status != null) {
			where.add(STATUS_SQL + " = ?");
			params.add(status.json());
		}
		if (query != null && !query.isBlank()) {
			where.add("(lower(i.number) LIKE ? OR lower(i.customer->>'name') LIKE ?)");
			String q = "%" + query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
					+ "%";
			params.add(q);
			params.add(q);
		}
		return jdbc.sql("""
				SELECT i.id, i.number, i.invoice_date, (i.customer->>'number')::int AS customer_number,
				       i.customer->>'name' AS customer_name, i.total_rappen, %s AS status
				FROM invoice i WHERE %s
				ORDER BY i.invoice_date DESC, i.year DESC, i.seq DESC""".formatted(STATUS_SQL,
				String.join(" AND ", where)))
			.params(params)
			.query((rs, n) -> new Summary(rs.getLong("id"), rs.getString("number"),
					rs.getObject("invoice_date", LocalDate.class), rs.getInt("customer_number"),
					rs.getString("customer_name"), rs.getLong("total_rappen"),
					Invoice.Status.valueOf(rs.getString("status").toUpperCase())))
			.list();
	}

	private Invoice withDetails(Invoice i) {
		List<Invoice.Line> lines = jdbc.sql("""
				SELECT position, name, category, qty, unit_price_rappen, vat_rate_permille, amount_rappen, note
				FROM invoice_line WHERE invoice_id = ? ORDER BY position""")
			.param(i.id())
			.query((rs, n) -> new Invoice.Line(rs.getInt("position"), rs.getString("name"), rs.getString("category"),
					rs.getInt("qty"), rs.getInt("unit_price_rappen"), rs.getInt("vat_rate_permille"),
					rs.getLong("amount_rappen"), rs.getString("note")))
			.list();
		List<Vat.Row> vat = jdbc.sql("""
				SELECT vat_rate_permille, gross_rappen, net_rappen, vat_rappen FROM invoice_vat
				WHERE invoice_id = ? ORDER BY vat_rate_permille DESC""")
			.param(i.id())
			.query((rs, n) -> new Vat.Row(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
			.list();
		List<Invoice.Payment> payments = jdbc.sql("""
				SELECT p.id, p.method, p.paid_on, r.display_name AS recorded_by, p.recorded_at, p.removed_at,
				       d.display_name AS removed_by, p.remove_reason
				FROM invoice_payment p JOIN staff r ON r.id = p.recorded_by LEFT JOIN staff d ON d.id = p.removed_by
				WHERE p.invoice_id = ? ORDER BY p.recorded_at, p.id""")
			.param(i.id())
			.query((rs, n) -> new Invoice.Payment(rs.getLong("id"), rs.getString("method"),
					rs.getObject("paid_on", LocalDate.class), rs.getString("recorded_by"),
					rs.getTimestamp("recorded_at").toInstant(), instant(rs, "removed_at"), rs.getString("removed_by"),
					rs.getString("remove_reason")))
			.list();
		return new Invoice(i.id(), i.number(), i.orderId(), i.customerId(), i.invoiceDate(), i.issuedAt(),
				i.issuedByName(), i.business(), i.customer(), i.vatEnabled(), i.vatUid(), i.totalRappen(), i.status(),
				i.cancelledAt(), i.cancelledByName(), i.cancelReason(), lines, vat, payments);
	}

	private Invoice map(ResultSet rs, int row) throws SQLException {
		return new Invoice(rs.getLong("id"), rs.getString("number"), rs.getLong("order_id"), rs.getLong("customer_id"),
				rs.getObject("invoice_date", LocalDate.class), rs.getTimestamp("issued_at").toInstant(),
				rs.getString("issued_by_name"), json.readValue(rs.getString("business"), Invoice.Business.class),
				json.readValue(rs.getString("customer"), Invoice.CustomerCopy.class), rs.getBoolean("vat_enabled"),
				rs.getString("vat_uid"), rs.getLong("total_rappen"),
				Invoice.Status.valueOf(rs.getString("status").toUpperCase()), instant(rs, "cancelled_at"),
				rs.getString("cancelled_by_name"), rs.getString("cancel_reason"), List.of(), List.of(), List.of());
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp t = rs.getTimestamp(column);
		return t == null ? null : t.toInstant();
	}

}
