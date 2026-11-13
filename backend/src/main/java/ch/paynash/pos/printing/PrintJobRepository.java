package ch.paynash.pos.printing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PrintJobRepository {

	private static final String SELECT = """
			SELECT j.id, j.order_id, j.invoice_id, j.item_type, j.printer_id, p.name AS printer_name, j.kind, j.content,
			       j.status, j.attempts, j.waiting_reason, j.last_error, j.first_attempt_at, j.next_attempt_at, j.reprint_of,
			       j.ticket_no, j.line_count, j.resolution, j.resolved_at, j.created_at,
			       EXISTS (SELECT 1 FROM print_job r WHERE r.reprint_of = j.id) AS reprinted
			FROM print_job j LEFT JOIN printer p ON p.id = j.printer_id""";

	private final JdbcClient jdbc;

	public PrintJobRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Exactly one of {@code orderId} and {@code invoiceId} is set. */
	public long insertPending(Long orderId, Long invoiceId, PrintPurpose type, long printerId, PrintJob.Kind kind,
			String content, Long reprintOf, int ticketNo, int lineCount, Instant now) {
		return jdbc.sql("""
				INSERT INTO print_job (order_id, invoice_id, item_type, printer_id, kind, content, reprint_of, ticket_no,
				                       line_count, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
			.params(orderId, invoiceId, type.dbValue(), printerId, kind.dbValue(), content, reprintOf, ticketNo,
					lineCount, Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	/** A ticket that cannot be delivered at all, e.g. because no printer serves its type. */
	public long insertFailed(Long orderId, Long invoiceId, PrintPurpose type, PrintJob.Kind kind, String content,
			Long reprintOf, int lineCount, String error, Instant now) {
		return jdbc.sql("""
				INSERT INTO print_job (order_id, invoice_id, item_type, kind, content, reprint_of, line_count, status,
				                       last_error, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'failed', ?, ?) RETURNING id""")
			.params(orderId, invoiceId, type.dbValue(), kind.dbValue(), content, reprintOf, lineCount, error,
					Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	public Optional<PrintJob> find(long id) {
		return jdbc.sql(SELECT + " WHERE j.id = ?").param(id).query(this::map).optional();
	}

	/** Locks the job row until the transaction ends, so two resolutions of one ticket are serialised. */
	public Optional<PrintJob> findForUpdate(long id) {
		return jdbc.sql(SELECT + " WHERE j.id = ? FOR UPDATE OF j").param(id).query(this::map).optional();
	}

	public List<PrintJob> findByInvoice(long invoiceId) {
		return jdbc.sql(SELECT + " WHERE j.invoice_id = ? ORDER BY j.id").param(invoiceId).query(this::map).list();
	}

	public List<PrintJob> findByOrder(long orderId) {
		return jdbc.sql(SELECT + " WHERE j.order_id = ? ORDER BY j.id").param(orderId).query(this::map).list();
	}

	/** Printers that have a pending job whose turn has come. */
	public List<Long> findPrintersWithDueJobs(Instant now) {
		return jdbc.sql("""
				SELECT DISTINCT printer_id FROM print_job
				WHERE status = 'pending' AND (next_attempt_at IS NULL OR next_attempt_at <= ?)""")
			.param(Timestamp.from(now))
			.query(Long.class)
			.list();
	}

	/** Whether the printer has a job waiting or being printed. */
	public boolean hasJobsInProgress(long printerId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM print_job WHERE printer_id = ? AND status IN ('pending', 'printing'))")
			.param(printerId)
			.query(Boolean.class)
			.single();
	}

	/** The oldest pending job of a printer. Later jobs wait for it, so tickets print in order. */
	public Optional<PrintJob> findNextPending(long printerId) {
		return jdbc.sql(SELECT + " WHERE j.printer_id = ? AND j.status = 'pending' ORDER BY j.id LIMIT 1")
			.param(printerId)
			.query(this::map)
			.optional();
	}

	/**
	 * Marks the job as being printed and counts the attempt, committed before the ticket goes to the printer, so a
	 * crash in between is detected at startup (design D2). The retry pauses run from the first attempt.
	 */
	public void startAttempt(long id, Instant now) {
		jdbc.sql("""
				UPDATE print_job SET status = 'printing', attempts = attempts + 1,
				                     first_attempt_at = coalesce(first_attempt_at, ?)
				WHERE id = ?""")
			.params(Timestamp.from(now), id)
			.update();
	}

	public void markPrinted(long id, Instant now) {
		jdbc.sql("""
				UPDATE print_job SET status = 'printed', printed_at = ?, waiting_reason = NULL, last_error = NULL,
				                     next_attempt_at = NULL
				WHERE id = ?""")
			.params(Timestamp.from(now), id)
			.update();
	}

	/** Nothing was printed: the job waits for its next attempt and keeps its place in the queue (design D1). */
	public void waitFor(long id, String reason, Instant next) {
		jdbc.sql("UPDATE print_job SET status = 'pending', waiting_reason = ?, next_attempt_at = ? WHERE id = ?")
			.params(reason, Timestamp.from(next), id)
			.update();
	}

	/** The printer may have printed the ticket; it is never sent again automatically. */
	public void markUnclear(long id, String error) {
		jdbc.sql("""
				UPDATE print_job SET status = 'unclear', last_error = ?, waiting_reason = NULL, next_attempt_at = NULL
				WHERE id = ?""")
			.params(error, id)
			.update();
	}

	public void markFailed(long id, String error) {
		jdbc.sql("""
				UPDATE print_job SET status = 'failed', last_error = ?, waiting_reason = NULL, next_attempt_at = NULL
				WHERE id = ?""")
			.params(error, id)
			.update();
	}

	/**
	 * Records how an unclear ticket was resolved (design D5). "printed" also sets the ticket to printed; "reprint"
	 * leaves it unclear, because the reprint replaces it. {@code stationId} is null when a waiter or admin reprinted.
	 */
	public void resolve(long id, PrintJob.Resolution resolution, Long stationId, Instant now) {
		jdbc.sql("""
				UPDATE print_job SET resolution = ?, resolved_at = ?, resolved_by_station = ?,
				                     status = CASE WHEN ? = 'printed' THEN 'printed' ELSE status END,
				                     printed_at = CASE WHEN ? = 'printed' THEN ? ELSE printed_at END
				WHERE id = ?""")
			.params(resolution.dbValue(), Timestamp.from(now), stationId, resolution.dbValue(), resolution.dbValue(),
					Timestamp.from(now), id)
			.update();
	}

	/** Jobs left in {@code printing} by a stopped process become unclear (design D2). Returns how many. */
	public int markInterruptedUnclear(String error) {
		return jdbc.sql("""
				UPDATE print_job SET status = 'unclear', last_error = ?, waiting_reason = NULL, next_attempt_at = NULL
				WHERE status = 'printing'""")
			.param(error)
			.update();
	}

	private PrintJob map(ResultSet rs, int row) throws SQLException {
		long printerId = rs.getLong("printer_id");
		Long printer = rs.wasNull() ? null : printerId;
		long reprintOf = rs.getLong("reprint_of");
		Long original = rs.wasNull() ? null : reprintOf;
		int ticketNo = rs.getInt("ticket_no");
		Integer number = rs.wasNull() ? null : ticketNo;
		return new PrintJob(rs.getLong("id"), rs.getObject("order_id", Long.class),
				rs.getObject("invoice_id", Long.class), PrintPurpose.fromDb(rs.getString("item_type")),
				printer, rs.getString("printer_name"), PrintJob.Kind.fromDb(rs.getString("kind")),
				rs.getString("content"), PrintJob.Status.fromDb(rs.getString("status")), rs.getInt("attempts"),
				rs.getString("waiting_reason"), rs.getString("last_error"), instant(rs, "first_attempt_at"),
				instant(rs, "next_attempt_at"), original, rs.getBoolean("reprinted"), number, rs.getInt("line_count"),
				PrintJob.Resolution.fromDb(rs.getString("resolution")), instant(rs, "resolved_at"),
				instant(rs, "created_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp t = rs.getTimestamp(column);
		return t == null ? null : t.toInstant();
	}

}
