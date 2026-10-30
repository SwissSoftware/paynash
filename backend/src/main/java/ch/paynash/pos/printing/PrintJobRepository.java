package ch.paynash.pos.printing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ch.paynash.pos.menu.ItemType;

@Repository
public class PrintJobRepository {

	private static final String SELECT = """
			SELECT j.id, j.order_id, j.item_type, j.printer_id, p.name AS printer_name, j.kind, j.content, j.status,
			       j.attempts, j.last_error, j.first_attempt_at, j.next_attempt_at, j.reprint_of, j.created_at,
			       EXISTS (SELECT 1 FROM print_job r WHERE r.reprint_of = j.id) AS reprinted
			FROM print_job j LEFT JOIN printer p ON p.id = j.printer_id""";

	private final JdbcClient jdbc;

	public PrintJobRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long insertPending(long orderId, ItemType type, long printerId, PrintJob.Kind kind, String content,
			Long reprintOf, Instant now) {
		return jdbc.sql("""
				INSERT INTO print_job (order_id, item_type, printer_id, kind, content, reprint_of, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""")
			.params(orderId, type.dbValue(), printerId, kind.dbValue(), content, reprintOf, Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	/** A ticket that cannot be delivered at all, e.g. because no printer serves its type. */
	public long insertFailed(long orderId, ItemType type, PrintJob.Kind kind, String content, Long reprintOf,
			String error, Instant now) {
		return jdbc.sql("""
				INSERT INTO print_job (order_id, item_type, kind, content, reprint_of, status, last_error, created_at)
				VALUES (?, ?, ?, ?, ?, 'failed', ?, ?) RETURNING id""")
			.params(orderId, type.dbValue(), kind.dbValue(), content, reprintOf, error, Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	public Optional<PrintJob> find(long id) {
		return jdbc.sql(SELECT + " WHERE j.id = ?").param(id).query(this::map).optional();
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

	/** The oldest pending job of a printer. Later jobs wait for it, so tickets print in order. */
	public Optional<PrintJob> findNextPending(long printerId) {
		return jdbc.sql(SELECT + " WHERE j.printer_id = ? AND j.status = 'pending' ORDER BY j.id LIMIT 1")
			.param(printerId)
			.query(this::map)
			.optional();
	}

	/** Counts the attempt and remembers when the first one started; the retry window runs from there. */
	public void startAttempt(long id, Instant now) {
		jdbc.sql("""
				UPDATE print_job SET attempts = attempts + 1, first_attempt_at = coalesce(first_attempt_at, ?)
				WHERE id = ?""")
			.params(Timestamp.from(now), id)
			.update();
	}

	public void markPrinted(long id, Instant now) {
		jdbc.sql("""
				UPDATE print_job SET status = 'printed', printed_at = ?, last_error = NULL, next_attempt_at = NULL
				WHERE id = ?""")
			.params(Timestamp.from(now), id)
			.update();
	}

	public void retryAt(long id, String error, Instant next) {
		jdbc.sql("UPDATE print_job SET last_error = ?, next_attempt_at = ? WHERE id = ?")
			.params(error, Timestamp.from(next), id)
			.update();
	}

	public void markFailed(long id, String error) {
		jdbc.sql("UPDATE print_job SET status = 'failed', last_error = ?, next_attempt_at = NULL WHERE id = ?")
			.params(error, id)
			.update();
	}

	private PrintJob map(ResultSet rs, int row) throws SQLException {
		long printerId = rs.getLong("printer_id");
		Long printer = rs.wasNull() ? null : printerId;
		long reprintOf = rs.getLong("reprint_of");
		Long original = rs.wasNull() ? null : reprintOf;
		return new PrintJob(rs.getLong("id"), rs.getLong("order_id"), ItemType.fromDb(rs.getString("item_type")),
				printer, rs.getString("printer_name"), PrintJob.Kind.fromDb(rs.getString("kind")),
				rs.getString("content"), PrintJob.Status.fromDb(rs.getString("status")), rs.getInt("attempts"),
				rs.getString("last_error"), instant(rs, "first_attempt_at"), instant(rs, "next_attempt_at"), original,
				rs.getBoolean("reprinted"), instant(rs, "created_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp t = rs.getTimestamp(column);
		return t == null ? null : t.toInstant();
	}

}
