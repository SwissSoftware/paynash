package ch.paynash.pos.printing;

import java.sql.ResultSet;
import java.sql.SQLException;
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

	private Printer map(ResultSet rs, int row) throws SQLException {
		return new Printer(rs.getLong("id"), rs.getString("name"), rs.getString("address"), rs.getString("device_id"),
				ItemType.fromDb(rs.getString("item_type")), rs.getBoolean("active"));
	}

}
