package ch.paynash.pos.order;

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
public class OrderRepository {

	private static final String ORDER_SELECT = """
			SELECT o.id, o.table_label, o.status, o.sent_by, s.display_name AS sent_by_name, o.sent_at, o.version,
			       o.void_reason
			FROM orders o JOIN staff s ON s.id = o.sent_by""";

	private static final String LINE_COLUMNS = """
			id, order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot, unit_price_snapshot, qty,
			note, status, cancel_reason""";

	private final JdbcClient jdbc;

	public OrderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * One row of the order history. {@code printStatus} sums up the order's tickets, ignoring tickets that were
	 * reprinted: {@code failed} if any failed (with the first error), else {@code pending} if any is still printing,
	 * else {@code printed}; null without tickets.
	 */
	public record OrderSummary(long id, String tableLabel, OrderStatus status, String waiterName, Instant sentAt,
			long totalRappen, String printStatus, String printError) {
	}

	// --- orders

	public long insertSent(String tableLabel, long staffId, Instant now) {
		return jdbc.sql("INSERT INTO orders (table_label, sent_by, sent_at) VALUES (?, ?, ?) RETURNING id")
			.params(tableLabel, staffId, Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	public Optional<Order> find(long id) {
		return jdbc.sql(ORDER_SELECT + " WHERE o.id = ?").param(id).query(this::mapOrder).optional();
	}

	/** Locks the order row until the transaction ends, so cancelling and voiding one order are serialised. */
	public Optional<Order> findForUpdate(long id) {
		return jdbc.sql(ORDER_SELECT + " WHERE o.id = ? FOR UPDATE OF o").param(id).query(this::mapOrder).optional();
	}

	/** Orders sent in {@code [from, to)}, newest first. */
	public List<OrderSummary> findSummaries(Instant from, Instant to) {
		return jdbc.sql("""
				WITH current_job AS (
				    SELECT j.order_id, j.id, j.status, j.last_error FROM print_job j
				    WHERE NOT EXISTS (SELECT 1 FROM print_job r WHERE r.reprint_of = j.id))
				SELECT o.id, o.table_label, o.status, s.display_name, o.sent_at,
				       coalesce(sum(l.qty * l.unit_price_snapshot) FILTER (WHERE l.status <> 'cancelled'), 0) AS total,
				       (SELECT CASE WHEN bool_or(c.status = 'failed') THEN 'failed'
				                    WHEN bool_or(c.status = 'pending') THEN 'pending'
				                    WHEN count(*) > 0 THEN 'printed' END
				        FROM current_job c WHERE c.order_id = o.id) AS print_status,
				       (SELECT c.last_error FROM current_job c
				        WHERE c.order_id = o.id AND c.status = 'failed' ORDER BY c.id LIMIT 1) AS print_error
				FROM orders o
				JOIN staff s ON s.id = o.sent_by
				LEFT JOIN order_line l ON l.order_id = o.id
				WHERE o.sent_at >= ? AND o.sent_at < ?
				GROUP BY o.id, s.display_name
				ORDER BY o.sent_at DESC, o.id DESC""")
			.params(Timestamp.from(from), Timestamp.from(to))
			.query((rs, row) -> new OrderSummary(rs.getLong("id"), rs.getString("table_label"),
					OrderStatus.fromDb(rs.getString("status")), rs.getString("display_name"),
					rs.getTimestamp("sent_at").toInstant(), rs.getLong("total"), rs.getString("print_status"),
					rs.getString("print_error")))
			.list();
	}

	public void voidOrder(long id, long staffId, String reason, Instant now) {
		jdbc.sql("""
				UPDATE orders SET status = 'voided', voided_by = ?, voided_at = ?, void_reason = ?,
				                  version = version + 1
				WHERE id = ?""")
			.params(staffId, Timestamp.from(now), reason, id)
			.update();
	}

	public void incrementVersion(long id) {
		jdbc.sql("UPDATE orders SET version = version + 1 WHERE id = ?").param(id).update();
	}

	// --- lines

	public List<OrderLine> findLines(long orderId) {
		return jdbc.sql("SELECT " + LINE_COLUMNS + " FROM order_line WHERE order_id = ? ORDER BY id")
			.param(orderId)
			.query(this::mapLine)
			.list();
	}

	public Optional<OrderLine> findLine(long orderId, long lineId) {
		return jdbc.sql("SELECT " + LINE_COLUMNS + " FROM order_line WHERE order_id = ? AND id = ?")
			.params(orderId, lineId)
			.query(this::mapLine)
			.optional();
	}

	public void insertLine(long orderId, long menuItemId, String name, ItemType type, String category,
			int unitPriceRappen, int qty, String note) {
		jdbc.sql("""
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				                        unit_price_snapshot, qty, note)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
			.params(orderId, menuItemId, name, type.dbValue(), category, unitPriceRappen, qty, note)
			.update();
	}

	public void cancelLine(long lineId, String reason, long staffId, Instant now) {
		jdbc.sql("""
				UPDATE order_line SET status = 'cancelled', cancel_reason = ?, cancelled_by = ?, cancelled_at = ?
				WHERE id = ? AND status = 'sent'""")
			.params(reason, staffId, Timestamp.from(now), lineId)
			.update();
	}

	/** Cancels every line of the order that is still sent. */
	public void cancelSentLines(long orderId, String reason, long staffId, Instant now) {
		jdbc.sql("""
				UPDATE order_line SET status = 'cancelled', cancel_reason = ?, cancelled_by = ?, cancelled_at = ?
				WHERE order_id = ? AND status = 'sent'""")
			.params(reason, staffId, Timestamp.from(now), orderId)
			.update();
	}

	private Order mapOrder(ResultSet rs, int row) throws SQLException {
		return new Order(rs.getLong("id"), rs.getString("table_label"), OrderStatus.fromDb(rs.getString("status")),
				rs.getLong("sent_by"), rs.getString("sent_by_name"), rs.getTimestamp("sent_at").toInstant(),
				rs.getInt("version"), rs.getString("void_reason"));
	}

	private OrderLine mapLine(ResultSet rs, int row) throws SQLException {
		return new OrderLine(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("menu_item_id"),
				rs.getString("name_snapshot"), ItemType.fromDb(rs.getString("type_snapshot")),
				rs.getString("category_snapshot"), rs.getInt("unit_price_snapshot"), rs.getInt("qty"),
				rs.getString("note"), LineStatus.fromDb(rs.getString("status")), rs.getString("cancel_reason"));
	}

}
