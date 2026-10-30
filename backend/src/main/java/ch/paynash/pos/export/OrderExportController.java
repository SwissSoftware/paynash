package ch.paynash.pos.export;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/** Orders as CSV for spreadsheets and accounting (spec order-export, design D9). Admins only (SecurityConfig). */
@RestController
@Tag(name = "Admin: export")
public class OrderExportController {

	static final List<String> HEADER = List.of("order_id", "table", "waiter", "sent_at", "order_status", "item_name",
			"item_type", "category", "quantity", "unit_price", "line_total", "line_status", "note");

	private final JdbcClient jdbc;

	private final ZoneId zone;

	public OrderExportController(JdbcClient jdbc, @Value("${pos.time-zone}") ZoneId zone) {
		this.jdbc = jdbc;
		this.zone = zone;
	}

	@GetMapping("/exports/orders.csv")
	@Operation(summary = "Order lines as CSV",
			description = "One row per order line of the orders sent from `from` to `to` (inclusive, local dates in "
					+ "Europe/Zurich). status is sent (default), voided, or all. Values come from the snapshots taken "
					+ "when the order was sent, so later menu changes do not alter the export.")
	public void export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "sent") String status, HttpServletResponse response) throws IOException {
		if (from.isAfter(to)) {
			throw ApiException.validation("from", "The start date must not be after the end date");
		}
		if (!List.of("sent", "voided", "all").contains(status)) {
			throw ApiException.validation("status", "Status must be sent, voided, or all");
		}
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename("orders_" + from + "_" + to + ".csv").build().toString());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		try (var csv = new CsvWriter(response.getOutputStream())) {
			csv.row(HEADER);
			var params = new ArrayList<Object>(List.of(Timestamp.from(from.atStartOfDay(zone).toInstant()),
					Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant())));
			if (!status.equals("all")) {
				params.add(status);
			}
			// Rows are written as they are read, so a long period does not build up in memory.
			jdbc.sql("""
					SELECT o.id, o.table_label, s.display_name, o.sent_at, o.status, l.name_snapshot,
					       l.type_snapshot, l.category_snapshot, l.qty, l.unit_price_snapshot, l.status AS line_status,
					       l.note
					FROM orders o
					JOIN staff s ON s.id = o.sent_by
					JOIN order_line l ON l.order_id = o.id
					WHERE o.sent_at >= ? AND o.sent_at < ?""" + (status.equals("all") ? "" : " AND o.status = ?") + """

					ORDER BY o.sent_at, o.id, l.id""")
				.params(params)
				.query((ResultSet rs) -> {
					try {
						csv.row(row(rs));
					}
					catch (IOException ex) {
						throw new SQLException("Writing the export failed", ex);
					}
				});
		}
	}

	private List<String> row(ResultSet rs) throws SQLException {
		boolean cancelled = rs.getString("line_status").equals("cancelled");
		long qty = rs.getInt("qty");
		long price = rs.getInt("unit_price_snapshot");
		var row = new ArrayList<String>();
		row.add(Long.toString(rs.getLong("id")));
		row.add(rs.getString("table_label"));
		row.add(rs.getString("display_name"));
		row.add(CsvWriter.timestamp(rs.getTimestamp("sent_at").toInstant(), zone));
		row.add(rs.getString("status"));
		row.add(rs.getString("name_snapshot"));
		row.add(rs.getString("type_snapshot"));
		row.add(rs.getString("category_snapshot"));
		row.add(Long.toString(qty));
		row.add(CsvWriter.amount(price));
		row.add(CsvWriter.amount(cancelled ? 0 : qty * price));
		row.add(rs.getString("line_status"));
		row.add(rs.getString("note"));
		return row;
	}

}
