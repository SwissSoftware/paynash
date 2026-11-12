package ch.paynash.pos.printing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ch.paynash.pos.menu.ItemType;

@Repository
public class PrinterRepository {

	private static final String COLUMNS = "id, name, address, device_id, item_type, active";

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

	public Optional<Printer> findActiveFor(ItemType type) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM printer WHERE active AND item_type = ?")
			.param(type.dbValue())
			.query(this::map)
			.optional();
	}

	/** The other active printer serving {@code type}, if any. */
	public Optional<Printer> findOtherActiveFor(ItemType type, long exceptId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM printer WHERE active AND item_type = ? AND id <> ?")
			.params(type.dbValue(), exceptId)
			.query(this::map)
			.optional();
	}

	public boolean existsByName(String name, long exceptId) {
		return jdbc.sql("SELECT count(*) FROM printer WHERE lower(name) = lower(?) AND id <> ?")
			.params(name, exceptId)
			.query(Long.class)
			.single() > 0;
	}

	public long insert(String name, String address, String deviceId, ItemType type, boolean active) {
		return jdbc.sql("""
				INSERT INTO printer (name, address, device_id, item_type, active)
				VALUES (?, ?, ?, ?, ?) RETURNING id""")
			.params(name, address, deviceId, type.dbValue(), active)
			.query(Long.class)
			.single();
	}

	public void update(long id, String name, String address, String deviceId, ItemType type, boolean active) {
		jdbc.sql("UPDATE printer SET name = ?, address = ?, device_id = ?, item_type = ?, active = ? WHERE id = ?")
			.params(name, address, deviceId, type.dbValue(), active, id)
			.update();
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
				ItemType.fromDb(rs.getString("item_type")), rs.getBoolean("active"));
	}

}
