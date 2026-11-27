package ch.paynash.pos.printing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Printers, the categories they print, and where sent lines were routed. */
@Repository
public class PrinterRepository {

	private static final String COLUMNS = "id, name, address, device_id, prints_tickets, prints_receipts, active";

	private static final String P_COLUMNS = "p.id, p.name, p.address, p.device_id, p.prints_tickets, p.prints_receipts, p.active";

	private final JdbcClient jdbc;

	public PrinterRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Printer> findAll() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM printer ORDER BY lower(name)").query(this::map).list();
	}

	public Optional<Printer> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM printer WHERE id = ?").param(id).query(this::map).optional();
	}

	public boolean existsByName(String name, long exceptId) {
		return jdbc.sql("SELECT count(*) FROM printer WHERE lower(name) = lower(?) AND id <> ?")
			.params(name, exceptId)
			.query(Long.class)
			.single() > 0;
	}

	public long insert(String name, String address, String deviceId, boolean printsTickets, boolean printsReceipts,
			boolean active) {
		return jdbc.sql("""
				INSERT INTO printer (name, address, device_id, prints_tickets, prints_receipts, active)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")
			.params(name, address, deviceId, printsTickets, printsReceipts, active)
			.query(Long.class)
			.single();
	}

	public void update(long id, String name, String address, String deviceId, boolean printsTickets,
			boolean printsReceipts, boolean active) {
		jdbc.sql("""
				UPDATE printer SET name = ?, address = ?, device_id = ?, prints_tickets = ?, prints_receipts = ?,
				                   active = ?
				WHERE id = ?""")
			.params(name, address, deviceId, printsTickets, printsReceipts, active, id)
			.update();
	}

	// --- category assignment (change printer-category-routing, design D1)

	/** A menu category as shown with a printer. */
	public record CategoryRef(long id, String name) {
	}

	/** Replaces the categories whose lines the printer prints. */
	public void setCategories(long printerId, Collection<Long> categoryIds) {
		jdbc.sql("DELETE FROM printer_category WHERE printer_id = ?").param(printerId).update();
		for (long categoryId : new LinkedHashSet<>(categoryIds)) {
			jdbc.sql("INSERT INTO printer_category (printer_id, category_id) VALUES (?, ?)")
				.params(printerId, categoryId)
				.update();
		}
	}

	public List<CategoryRef> categories(long printerId) {
		return jdbc.sql("""
				SELECT c.id, c.name FROM printer_category pc JOIN category c ON c.id = pc.category_id
				WHERE pc.printer_id = ? ORDER BY c.display_order, lower(c.name)""")
			.param(printerId)
			.query((rs, row) -> new CategoryRef(rs.getLong("id"), rs.getString("name")))
			.list();
	}

	/** The ids among {@code categoryIds} that do not exist. */
	public List<Long> missingCategories(Collection<Long> categoryIds) {
		if (categoryIds.isEmpty()) {
			return List.of();
		}
		List<Long> existing = jdbc.sql("SELECT id FROM category WHERE id = ANY (?)")
			.param(categoryIds.toArray(Long[]::new))
			.query(Long.class)
			.list();
		return categoryIds.stream().filter(id -> !existing.contains(id)).distinct().toList();
	}

	/** Categories that no active printer with tickets on prints (spec ticket-printing, "Categories that are not printed"). */
	public List<CategoryRef> unprintedCategories() {
		return jdbc.sql("""
				SELECT c.id, c.name FROM category c
				WHERE NOT EXISTS (SELECT 1 FROM printer_category pc JOIN printer p ON p.id = pc.printer_id
				                  WHERE pc.category_id = c.id AND p.active AND p.prints_tickets)
				ORDER BY c.display_order, lower(c.name)""")
			.query((rs, row) -> new CategoryRef(rs.getLong("id"), rs.getString("name")))
			.list();
	}

	/**
	 * For each menu item, the printers with tickets on that its category is assigned to, active or not, ordered by
	 * name. Items whose category goes to no such printer are missing from the map.
	 */
	public Map<Long, List<Printer>> ticketPrintersByItem(Collection<Long> menuItemIds) {
		Map<Long, List<Printer>> result = new HashMap<>();
		if (menuItemIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT mi.id AS menu_item_id, " + P_COLUMNS + """
				 FROM menu_item mi
				JOIN printer_category pc ON pc.category_id = mi.category_id
				JOIN printer p ON p.id = pc.printer_id AND p.prints_tickets
				WHERE mi.id = ANY (?) ORDER BY lower(p.name)""")
			.param(menuItemIds.toArray(Long[]::new))
			.query((RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("menu_item_id"), k -> new ArrayList<>())
				.add(map(rs, 0)));
		return result;
	}

	/** The printers with receipts on, active or not, ordered by name. */
	public List<Printer> receiptPrinters() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM printer WHERE prints_receipts ORDER BY lower(name)")
			.query(this::map)
			.list();
	}

	/** Remembers that a sent line was routed to a printer (design D2). */
	public void recordLinePrinter(long orderLineId, long printerId) {
		jdbc.sql("INSERT INTO order_line_printer (order_line_id, printer_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
			.params(orderLineId, printerId)
			.update();
	}

	/** For each line, the printers it was routed to when it was sent, ordered by name. */
	public Map<Long, List<Printer>> printersByLine(Collection<Long> orderLineIds) {
		Map<Long, List<Printer>> result = new HashMap<>();
		if (orderLineIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT olp.order_line_id, " + P_COLUMNS + """
				 FROM order_line_printer olp JOIN printer p ON p.id = olp.printer_id
				WHERE olp.order_line_id = ANY (?) ORDER BY lower(p.name)""")
			.param(orderLineIds.toArray(Long[]::new))
			.query((RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("order_line_id"), k -> new ArrayList<>())
				.add(map(rs, 0)));
		return result;
	}

	/**
	 * The printer's next ticket number (design D3). The row stays locked until the caller's transaction ends, so
	 * concurrent orders get consecutive numbers in the order their tickets are queued.
	 */
	public int nextTicketNo(long id) {
		return jdbc.sql("UPDATE printer SET last_ticket_no = last_ticket_no + 1 WHERE id = ? RETURNING last_ticket_no")
			.param(id)
			.query(Integer.class)
			.single();
	}

	/** A station that shows a printer, and when it last polled. */
	public record StationSeen(long id, String name, Instant lastSeenAt) {
	}

	public List<StationSeen> stationsShowing(long printerId) {
		return jdbc.sql("""
				SELECT s.id, s.display_name, s.last_seen_at FROM station_printer sp JOIN staff s ON s.id = sp.station_id
				WHERE sp.printer_id = ? AND s.active ORDER BY lower(s.display_name)""")
			.param(printerId)
			.query((rs, row) -> {
				var seen = rs.getTimestamp("last_seen_at");
				return new StationSeen(rs.getLong("id"), rs.getString("display_name"),
						seen == null ? null : seen.toInstant());
			})
			.list();
	}

	private Printer map(ResultSet rs, int row) throws SQLException {
		return new Printer(rs.getLong("id"), rs.getString("name"), rs.getString("address"), rs.getString("device_id"),
				rs.getBoolean("prints_tickets"), rs.getBoolean("prints_receipts"), rs.getBoolean("active"));
	}

}
