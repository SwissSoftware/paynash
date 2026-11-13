package ch.paynash.pos.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.customer.Customer;
import ch.paynash.pos.invoice.InvoiceService;

/** Builds the sales report from the invoice tables only (spec sales-reporting, add-salon-mode design D10). */
@Service
public class ReportService {

	static final int MAX_DAYS = 366;

	/** Not cancelled, i.e. counts as revenue. */
	private static final String COUNTS = "i.status = 'issued'";

	private static final String CUSTOMER_NAME = """
			CASE WHEN cu.anonymized_at IS NOT NULL THEN '%s'
			     ELSE trim(coalesce(cu.first_name, '') || ' ' || coalesce(cu.last_name, '')) END"""
		.formatted(Customer.DELETED_NAME);

	private final JdbcClient jdbc;

	private final Clock clock;

	private final ZoneId zone;

	public ReportService(JdbcClient jdbc, Clock clock, @Value("${pos.time-zone}") ZoneId zone) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.zone = zone;
	}

	/** {@code from} and {@code to} default to the current month; the range is at most 366 days. */
	@Transactional(readOnly = true)
	public SalesReport report(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
		LocalDate start = from != null ? from : (to != null ? to.withDayOfMonth(1) : today.withDayOfMonth(1));
		LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
		if (end.isBefore(start)) {
			throw ApiException.validation("to", "The end date must not be before the start date");
		}
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
			throw ApiException.validation("to", "The period must be at most " + MAX_DAYS + " days");
		}
		var days = days(start, end);
		long revenue = days.stream().mapToLong(SalesReport.Day::revenueRappen).sum();
		int count = days.stream().mapToInt(SalesReport.Day::invoiceCount).sum();
		long paid = jdbc.sql("""
				SELECT coalesce(sum(i.total_rappen), 0) FROM invoice i
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				  AND EXISTS (SELECT 1 FROM invoice_payment p WHERE p.invoice_id = i.id AND p.removed_at IS NULL)"""
			.formatted(COUNTS)).params(start, end).query(Long.class).single();
		var summary = new SalesReport.Summary(revenue, count, paid, revenue - paid, byMethod(start, end),
				vat(start, end));
		return new SalesReport(start, end, summary, days, services(start, end), customers(start, end),
				invoices(start, end), lines(start, end));
	}

	private List<SalesReport.Day> days(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT d::date AS day, coalesce(sum(i.total_rappen), 0) AS revenue, count(i.id) AS invoices
				FROM generate_series(?::date, ?::date, interval '1 day') d
				LEFT JOIN invoice i ON i.invoice_date = d::date AND %s
				GROUP BY d ORDER BY d""".formatted(COUNTS))
			.params(from, to)
			.query((rs, n) -> new SalesReport.Day(rs.getObject("day", LocalDate.class), rs.getLong("revenue"),
					rs.getInt("invoices")))
			.list();
	}

	private List<SalesReport.MethodTotal> byMethod(LocalDate from, LocalDate to) {
		Map<String, Long> sums = jdbc.sql("""
				SELECT p.method, sum(i.total_rappen) AS total FROM invoice i
				JOIN invoice_payment p ON p.invoice_id = i.id AND p.removed_at IS NULL
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				GROUP BY p.method""".formatted(COUNTS))
			.params(from, to)
			.query((rs, n) -> Map.entry(rs.getString("method"), rs.getLong("total")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return InvoiceService.METHODS.stream()
			.map(m -> new SalesReport.MethodTotal(m, sums.getOrDefault(m, 0L)))
			.toList();
	}

	private List<SalesReport.VatTotal> vat(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT v.vat_rate_permille, sum(v.gross_rappen), sum(v.net_rappen), sum(v.vat_rappen)
				FROM invoice_vat v JOIN invoice i ON i.id = v.invoice_id
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				GROUP BY v.vat_rate_permille ORDER BY v.vat_rate_permille DESC""".formatted(COUNTS))
			.params(from, to)
			.query((rs, n) -> new SalesReport.VatTotal(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
			.list();
	}

	private List<SalesReport.Service> services(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT l.name, sum(l.qty) AS qty, sum(l.amount_rappen) AS revenue
				FROM invoice_line l JOIN invoice i ON i.id = l.invoice_id
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				GROUP BY l.name ORDER BY revenue DESC, l.name""".formatted(COUNTS))
			.params(from, to)
			.query((rs, n) -> new SalesReport.Service(rs.getString("name"), rs.getInt("qty"), rs.getLong("revenue")))
			.list();
	}

	private List<SalesReport.Customer> customers(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT cu.number, %s AS name, count(i.id) AS invoices, sum(i.total_rappen) AS revenue
				FROM invoice i JOIN customer cu ON cu.id = i.customer_id
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				GROUP BY cu.id ORDER BY revenue DESC, cu.number""".formatted(CUSTOMER_NAME, COUNTS))
			.params(from, to)
			.query((rs, n) -> new SalesReport.Customer(rs.getInt("number"), rs.getString("name"),
					rs.getInt("invoices"), rs.getLong("revenue")))
			.list();
	}

	private List<SalesReport.InvoiceRow> invoices(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT i.number, i.invoice_date, cu.number AS customer_number, %s AS customer_name, i.total_rappen,
				       coalesce((SELECT sum(v.vat_rappen) FROM invoice_vat v WHERE v.invoice_id = i.id), 0) AS vat,
				       CASE WHEN i.status = 'cancelled' THEN 'cancelled' WHEN p.id IS NOT NULL THEN 'paid'
				            ELSE 'open' END AS status,
				       CASE WHEN i.status = 'issued' THEN p.method END AS method,
				       CASE WHEN i.status = 'issued' THEN p.paid_on END AS paid_on
				FROM invoice i JOIN customer cu ON cu.id = i.customer_id
				LEFT JOIN invoice_payment p ON p.invoice_id = i.id AND p.removed_at IS NULL
				WHERE i.invoice_date BETWEEN ? AND ?
				ORDER BY i.invoice_date, i.year, i.seq""".formatted(CUSTOMER_NAME))
			.params(from, to)
			.query((rs, n) -> new SalesReport.InvoiceRow(rs.getString("number"),
					rs.getObject("invoice_date", LocalDate.class), rs.getInt("customer_number"),
					rs.getString("customer_name"), rs.getLong("total_rappen"), rs.getLong("vat"),
					rs.getString("status"), rs.getString("method"), rs.getObject("paid_on", LocalDate.class)))
			.list();
	}

	private List<SalesReport.LineRow> lines(LocalDate from, LocalDate to) {
		return jdbc.sql("""
				SELECT i.number, i.invoice_date, cu.number AS customer_number, %s AS customer_name, l.name, l.category,
				       l.qty, l.unit_price_rappen, l.vat_rate_permille, l.amount_rappen
				FROM invoice_line l JOIN invoice i ON i.id = l.invoice_id JOIN customer cu ON cu.id = i.customer_id
				WHERE %s AND i.invoice_date BETWEEN ? AND ?
				ORDER BY i.invoice_date, i.year, i.seq, l.position""".formatted(CUSTOMER_NAME, COUNTS))
			.params(from, to)
			.query((rs, n) -> new SalesReport.LineRow(rs.getString("number"),
					rs.getObject("invoice_date", LocalDate.class), rs.getInt("customer_number"),
					rs.getString("customer_name"), rs.getString("name"), rs.getString("category"), rs.getInt("qty"),
					rs.getLong("unit_price_rappen"), rs.getInt("vat_rate_permille"), rs.getLong("amount_rappen")))
			.list();
	}

}
