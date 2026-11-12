package ch.paynash.pos.station;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ch.paynash.pos.menu.ItemType;

/** Reads for the print station (spec print-station). */
@Repository
public class StationRepository {

	private final JdbcClient jdbc;

	public StationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** A ticket as the station lists it. */
	public record StationTicket(long id, long orderId, Integer ticketNo, String printerName, String kind, String status,
			String waitingReason, String error, String tableLabel, String waiterName, int lineCount, String resolution,
			boolean reprinted, Instant createdAt) {
	}

	/** Counts that sum up a printer's queue for the station. */
	public record QueueSummary(Integer lastPrintedTicketNo, int waiting, Instant oldestWaitingSince, int unclear) {
	}

	public record AssignedPrinter(long id, String name, ItemType type, boolean active) {
	}

	public List<AssignedPrinter> assignedPrinters(long stationId) {
		return jdbc.sql("""
				SELECT p.id, p.name, p.item_type, p.active FROM station_printer sp JOIN printer p ON p.id = sp.printer_id
				WHERE sp.station_id = ? ORDER BY lower(p.name)""")
			.param(stationId)
			.query((rs, row) -> new AssignedPrinter(rs.getLong("id"), rs.getString("name"),
					ItemType.fromDb(rs.getString("item_type")), rs.getBoolean("active")))
			.list();
	}

	/**
	 * The printer's tickets created since {@code since}, plus every older ticket that still needs attention (waiting,
	 * unclear and unresolved, or failed and not reprinted), so a problem from before midnight stays visible. Unresolved
	 * unclear tickets come first, then newest first.
	 */
	public List<StationTicket> tickets(long printerId, Instant since) {
		return jdbc.sql("""
				SELECT j.id, j.order_id, j.ticket_no, p.name AS printer_name, j.kind, j.status, j.waiting_reason,
				       j.last_error, o.table_label, s.display_name AS waiter_name, j.line_count, j.resolution,
				       j.created_at, r.reprinted
				FROM print_job j
				JOIN printer p ON p.id = j.printer_id
				JOIN orders o ON o.id = j.order_id
				JOIN staff s ON s.id = o.sent_by
				CROSS JOIN LATERAL (SELECT EXISTS (SELECT 1 FROM print_job x WHERE x.reprint_of = j.id) AS reprinted) r
				WHERE j.printer_id = ?
				  AND (j.created_at >= ?
				       OR j.status IN ('pending', 'printing')
				       OR (j.status = 'unclear' AND j.resolution IS NULL)
				       OR (j.status = 'failed' AND NOT r.reprinted))
				ORDER BY (j.status = 'unclear' AND j.resolution IS NULL) DESC, j.id DESC""")
			.params(printerId, Timestamp.from(since))
			.query(this::mapTicket)
			.list();
	}

	public QueueSummary summary(long printerId) {
		return jdbc.sql("""
				SELECT (SELECT max(ticket_no) FROM print_job WHERE printer_id = ? AND status = 'printed') AS last_printed,
				       count(*) FILTER (WHERE status IN ('pending', 'printing')) AS waiting,
				       min(created_at) FILTER (WHERE status IN ('pending', 'printing')) AS oldest_waiting,
				       count(*) FILTER (WHERE status = 'unclear' AND resolution IS NULL) AS unclear
				FROM print_job WHERE printer_id = ?""")
			.params(printerId, printerId)
			.query((rs, row) -> {
				int last = rs.getInt("last_printed");
				Integer lastPrinted = rs.wasNull() ? null : last;
				Timestamp oldest = rs.getTimestamp("oldest_waiting");
				return new QueueSummary(lastPrinted, rs.getInt("waiting"), oldest == null ? null : oldest.toInstant(),
						rs.getInt("unclear"));
			})
			.single();
	}

	/** Remembers when the station last polled, shown to admins (design risk: a sleeping station tablet). */
	public void touch(long stationId, Instant now) {
		jdbc.sql("UPDATE staff SET last_seen_at = ? WHERE id = ?").params(Timestamp.from(now), stationId).update();
	}

	private StationTicket mapTicket(ResultSet rs, int row) throws SQLException {
		int no = rs.getInt("ticket_no");
		Integer ticketNo = rs.wasNull() ? null : no;
		return new StationTicket(rs.getLong("id"), rs.getLong("order_id"), ticketNo, rs.getString("printer_name"),
				rs.getString("kind"), rs.getString("status"), rs.getString("waiting_reason"),
				rs.getString("last_error"), rs.getString("table_label"), rs.getString("waiter_name"),
				rs.getInt("line_count"), rs.getString("resolution"), rs.getBoolean("reprinted"),
				rs.getTimestamp("created_at").toInstant());
	}

}
