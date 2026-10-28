package ch.paynash.pos.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ch.paynash.pos.staff.Role;

@Repository
public class SessionRepository {

	private final JdbcClient jdbc;

	public SessionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(String tokenHash, long staffId, Instant createdAt, Instant expiresAt) {
		jdbc.sql("INSERT INTO session (token_hash, staff_id, created_at, expires_at) VALUES (?, ?, ?, ?)")
			.params(tokenHash, staffId, Timestamp.from(createdAt), Timestamp.from(expiresAt))
			.update();
	}

	/** Finds the staff member of a session that has not expired and whose account is active. */
	public Optional<AuthenticatedStaff> findValid(String tokenHash, Instant now) {
		return jdbc.sql("""
				SELECT s.id, s.username, s.display_name, s.role
				FROM session x JOIN staff s ON s.id = x.staff_id
				WHERE x.token_hash = ? AND x.expires_at > ? AND s.active""")
			.params(tokenHash, Timestamp.from(now))
			.query((rs, row) -> new AuthenticatedStaff(rs.getLong("id"), rs.getString("username"),
					rs.getString("display_name"), Role.fromDb(rs.getString("role")), tokenHash))
			.optional();
	}

	public void delete(String tokenHash) {
		jdbc.sql("DELETE FROM session WHERE token_hash = ?").param(tokenHash).update();
	}

	public void deleteAllForStaff(long staffId) {
		jdbc.sql("DELETE FROM session WHERE staff_id = ?").param(staffId).update();
	}

	public void deleteExpired(Instant now) {
		jdbc.sql("DELETE FROM session WHERE expires_at <= ?").param(Timestamp.from(now)).update();
	}

}
