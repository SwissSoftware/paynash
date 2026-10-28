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
			SELECT o.id, o.table_label, o.status, o.opened_by, s.display_name AS opened_by_name, o.opened_at,
			       o.version, o.void_reason
			FROM orders o JOIN staff s ON s.id = o.opened_by""";

	private static final String LINE_COLUMNS = """
			id, order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot, unit_price_snapshot, qty,
			note, status, round_id, version, cancel_reason""";

	private final JdbcClient jdbc;

	public OrderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** One row of the open orders overview. */
	public record OpenOrderSummary(long id, String tableLabel, String waiterName, Instant openedAt, long totalRappen,
			int unsentLines) {
	}

	// --- orders

	/**
	 * Inserts an open order unless the table already has one (partial unique index). Returns the new id, or empty if
	 * an open order exists.
	 */
	public Optional<Long> insertOpenIfAbsent(String tableLabel, long staffId, Instant now) {
		return jdbc.sql("""
				INSERT INTO orders (table_label, opened_by, opened_at) VALUES (?, ?, ?)
				ON CONFLICT (table_label) WHERE status = 'open' DO NOTHING
				RETURNING id""")
			.params(tableLabel, staffId, Timestamp.from(now))
			.query(Long.class)
			.optional();
	}

	public Optional<Order> find(long id) {
		return jdbc.sql(ORDER_SELECT + " WHERE o.id = ?").param(id).query(this::mapOrder).optional();
	}

	/** Locks the order row until the transaction ends, so changes to one order are serialised. */
	public Optional<Order> findForUpdate(long id) {
		return jdbc.sql(ORDER_SELECT + " WHERE o.id = ? FOR UPDATE OF o").param(id).query(this::mapOrder).optional();
	}

	public Optional<Order> findOpenByTable(String tableLabel) {
		return jdbc.sql(ORDER_SELECT + " WHERE o.table_label = ? AND o.status = 'open'")
			.param(tableLabel)
			.query(this::mapOrder)
			.optional();
	}

	public List<OpenOrderSummary> findOpenSummaries() {
		return jdbc.sql("""
				SELECT o.id, o.table_label, s.display_name, o.opened_at,
				       coalesce(sum(l.qty * l.unit_price_snapshot) FILTER (WHERE l.status <> 'cancelled'), 0) AS total,
				       count(l.id) FILTER (WHERE l.status = 'unsent') AS unsent
				FROM orders o
				JOIN staff s ON s.id = o.opened_by
				LEFT JOIN order_line l ON l.order_id = o.id
				WHERE o.status = 'open'
				GROUP BY o.id, s.display_name
				ORDER BY o.opened_at, o.id""")
			.query((rs, row) -> new OpenOrderSummary(rs.getLong("id"), rs.getString("table_label"),
					rs.getString("display_name"), rs.getTimestamp("opened_at").toInstant(), rs.getLong("total"),
					rs.getInt("unsent")))
			.list();
	}

	public void incrementVersion(long id) {
		jdbc.sql("UPDATE orders SET version = version + 1 WHERE id = ?").param(id).update();
	}

	public void voidOrder(long id, long staffId, String reason, Instant now) {
		jdbc.sql("""
				UPDATE orders SET status = 'voided', voided_by = ?, voided_at = ?, void_reason = ?,
				                  version = version + 1
				WHERE id = ?""")
			.params(staffId, Timestamp.from(now), reason, id)
			.update();
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

	public long insertLine(long orderId, long menuItemId, String name, ItemType type, String category,
			int unitPriceRappen, int qty, String note, long staffId, Instant now) {
		return jdbc.sql("""
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				                        unit_price_snapshot, qty, note, added_by, added_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
			.params(orderId, menuItemId, name, type.dbValue(), category, unitPriceRappen, qty, note, staffId,
					Timestamp.from(now))
			.query(Long.class)
			.single();
	}

	public void updateUnsentLine(long lineId, int qty, String note) {
		jdbc.sql("UPDATE order_line SET qty = ?, note = ?, version = version + 1 WHERE id = ? AND status = 'unsent'")
			.params(qty, note, lineId)
			.update();
	}

	public void deleteUnsentLine(long lineId) {
		jdbc.sql("DELETE FROM order_line WHERE id = ? AND status = 'unsent'").param(lineId).update();
	}

	/** Moves every unsent line of the order into the round. */
	public void markUnsentAsSent(long orderId, long roundId) {
		jdbc.sql("""
				UPDATE order_line SET status = 'sent', round_id = ?, version = version + 1
				WHERE order_id = ? AND status = 'unsent'""")
			.params(roundId, orderId)
			.update();
	}

	public void cancelLine(long lineId, String reason, long staffId, Instant now) {
		jdbc.sql("""
				UPDATE order_line SET status = 'cancelled', cancel_reason = ?, cancelled_by = ?, cancelled_at = ?,
				                      version = version + 1
				WHERE id = ? AND status = 'sent'""")
			.params(reason, staffId, Timestamp.from(now), lineId)
			.update();
	}

	// --- rounds

	public List<OrderRound> findRounds(long orderId) {
		return jdbc.sql("""
				SELECT r.id, r.order_id, r.number, r.sent_by, s.display_name, r.sent_at
				FROM order_round r JOIN staff s ON s.id = r.sent_by
				WHERE r.order_id = ? ORDER BY r.number""")
			.param(orderId)
			.query(this::mapRound)
			.list();
	}

	public OrderRound insertRound(long orderId, long staffId, Instant now) {
		long id = jdbc.sql("""
				INSERT INTO order_round (order_id, number, sent_by, sent_at)
				VALUES (?, (SELECT coalesce(max(number), 0) + 1 FROM order_round WHERE order_id = ?), ?, ?)
				RETURNING id""")
			.params(orderId, orderId, staffId, Timestamp.from(now))
			.query(Long.class)
			.single();
		return findRounds(orderId).stream().filter(r -> r.id() == id).findFirst().orElseThrow();
	}

	private Order mapOrder(ResultSet rs, int row) throws SQLException {
		return new Order(rs.getLong("id"), rs.getString("table_label"), OrderStatus.fromDb(rs.getString("status")),
				rs.getLong("opened_by"), rs.getString("opened_by_name"), rs.getTimestamp("opened_at").toInstant(),
				rs.getInt("version"), rs.getString("void_reason"));
	}

	private OrderLine mapLine(ResultSet rs, int row) throws SQLException {
		return new OrderLine(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("menu_item_id"),
				rs.getString("name_snapshot"), ItemType.fromDb(rs.getString("type_snapshot")),
				rs.getString("category_snapshot"), rs.getInt("unit_price_snapshot"), rs.getInt("qty"),
				rs.getString("note"), LineStatus.fromDb(rs.getString("status")), (Long) rs.getObject("round_id"),
				rs.getInt("version"), rs.getString("cancel_reason"));
	}

	private OrderRound mapRound(ResultSet rs, int row) throws SQLException {
		return new OrderRound(rs.getLong("id"), rs.getLong("order_id"), rs.getInt("number"), rs.getLong("sent_by"),
				rs.getString("display_name"), rs.getTimestamp("sent_at").toInstant());
	}

}
