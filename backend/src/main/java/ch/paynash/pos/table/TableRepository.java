package ch.paynash.pos.table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TableRepository {

	private static final String COLUMNS = "id, number, name, active";

	private final JdbcClient jdbc;

	public TableRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<DiningTable> findAll() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM dining_table").query(this::map).list();
	}

	public Optional<DiningTable> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM dining_table WHERE id = ?").param(id).query(this::map).optional();
	}

	/** Whether another table than {@code exceptId} already uses {@code number}, including inactive ones. */
	public boolean existsByNumber(String number, long exceptId) {
		return jdbc.sql("SELECT count(*) FROM dining_table WHERE upper(number) = upper(?) AND id <> ?")
			.params(number, exceptId)
			.query(Long.class)
			.single() > 0;
	}

	public long insert(String number, String name, boolean active) {
		return jdbc.sql("INSERT INTO dining_table (number, name, active) VALUES (?, ?, ?) RETURNING id")
			.params(number, name, active)
			.query(Long.class)
			.single();
	}

	public void update(long id, String number, String name, boolean active) {
		jdbc.sql("UPDATE dining_table SET number = ?, name = ?, active = ? WHERE id = ?")
			.params(number, name, active, id)
			.update();
	}

	public void delete(long id) {
		jdbc.sql("DELETE FROM dining_table WHERE id = ?").param(id).update();
	}

	private DiningTable map(ResultSet rs, int row) throws SQLException {
		return new DiningTable(rs.getLong("id"), rs.getString("number"), rs.getString("name"), rs.getBoolean("active"));
	}

}
