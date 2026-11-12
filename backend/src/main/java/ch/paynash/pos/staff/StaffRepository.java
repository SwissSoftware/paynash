package ch.paynash.pos.staff;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository {

	private static final String COLUMNS = "id, username, display_name, role, active, pin_hash, failed_attempts, locked_until";

	private final JdbcClient jdbc;

	public StaffRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Staff> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM staff WHERE id = ?").param(id).query(this::map).optional();
	}

	public Optional<Staff> findByUsername(String username) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM staff WHERE lower(username) = lower(?)")
			.param(username)
			.query(this::map)
			.optional();
	}

	public List<Staff> findAll() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM staff ORDER BY lower(display_name)").query(this::map).list();
	}

	public boolean existsByUsername(String username) {
		return jdbc.sql("SELECT count(*) FROM staff WHERE lower(username) = lower(?)")
			.param(username)
			.query(Long.class)
			.single() > 0;
	}

	public long countActiveAdmins() {
		return jdbc.sql("SELECT count(*) FROM staff WHERE role = 'admin' AND active").query(Long.class).single();
	}

	public long insert(String username, String displayName, Role role, String pinHash) {
		return jdbc.sql("""
				INSERT INTO staff (username, display_name, role, pin_hash)
				VALUES (?, ?, ?, ?) RETURNING id""")
			.params(username, displayName, role.dbValue(), pinHash)
			.query(Long.class)
			.single();
	}

	public void update(long id, String displayName, Role role, boolean active) {
		jdbc.sql("UPDATE staff SET display_name = ?, role = ?, active = ? WHERE id = ?")
			.params(displayName, role.dbValue(), active, id)
			.update();
	}

	/** Sets a new PIN and clears any lockout. */
	public void updatePin(long id, String pinHash) {
		jdbc.sql("UPDATE staff SET pin_hash = ?, failed_attempts = 0, locked_until = NULL WHERE id = ?")
			.params(pinHash, id)
			.update();
	}

	/** Atomically increments the failed-login counter and returns the new value. */
	public int incrementFailedAttempts(long id) {
		return jdbc.sql("UPDATE staff SET failed_attempts = failed_attempts + 1 WHERE id = ? RETURNING failed_attempts")
			.param(id)
			.query(Integer.class)
			.single();
	}

	public void lock(long id, Instant until) {
		jdbc.sql("UPDATE staff SET failed_attempts = 0, locked_until = ? WHERE id = ?")
			.params(Timestamp.from(until), id)
			.update();
	}

	public void resetFailedLogins(long id) {
		jdbc.sql("UPDATE staff SET failed_attempts = 0, locked_until = NULL WHERE id = ?").param(id).update();
	}

	/** The printers a station shows, by id. */
	public List<Long> findStationPrinters(long stationId) {
		return jdbc.sql("SELECT printer_id FROM station_printer WHERE station_id = ? ORDER BY printer_id")
			.param(stationId)
			.query(Long.class)
			.list();
	}

	public void setStationPrinters(long stationId, List<Long> printerIds) {
		jdbc.sql("DELETE FROM station_printer WHERE station_id = ?").param(stationId).update();
		for (long printerId : printerIds) {
			jdbc.sql("INSERT INTO station_printer (station_id, printer_id) VALUES (?, ?)")
				.params(stationId, printerId)
				.update();
		}
	}

	/** How many of the given printer ids exist. */
	public long countPrinters(List<Long> printerIds) {
		return jdbc.sql("SELECT count(*) FROM printer WHERE id = ANY(?)")
			.param(printerIds.toArray(Long[]::new))
			.query(Long.class)
			.single();
	}

	private Staff map(ResultSet rs, int row) throws SQLException {
		Timestamp lockedUntil = rs.getTimestamp("locked_until");
		return new Staff(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
				Role.fromDb(rs.getString("role")), rs.getBoolean("active"), rs.getString("pin_hash"),
				rs.getInt("failed_attempts"), lockedUntil == null ? null : lockedUntil.toInstant());
	}

}
