package ch.paynash.pos.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

	/** A stored request. {@code status} is null while the first request is still being processed. */
	public record StoredRequest(String fingerprint, Integer status, String contentType, byte[] body) {
	}

	private final JdbcClient jdbc;

	public IdempotencyRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Claims the key. Returns false if it was already used. */
	public boolean tryClaim(long staffId, String key, String fingerprint, Instant now) {
		return jdbc.sql("""
				INSERT INTO idempotency_key (key, staff_id, fingerprint, created_at) VALUES (?, ?, ?, ?)
				ON CONFLICT DO NOTHING""")
			.params(key, staffId, fingerprint, Timestamp.from(now))
			.update() == 1;
	}

	public Optional<StoredRequest> find(long staffId, String key) {
		return jdbc.sql("""
				SELECT fingerprint, response_status, response_type, response_body
				FROM idempotency_key WHERE staff_id = ? AND key = ?""")
			.params(staffId, key)
			.query((rs, row) -> new StoredRequest(rs.getString("fingerprint"),
					(Integer) rs.getObject("response_status"), rs.getString("response_type"),
					rs.getBytes("response_body")))
			.optional();
	}

	public void complete(long staffId, String key, int status, String contentType, byte[] body) {
		jdbc.sql("""
				UPDATE idempotency_key SET response_status = ?, response_type = ?, response_body = ?
				WHERE staff_id = ? AND key = ?""")
			.params(status, contentType, body, staffId, key)
			.update();
	}

	public void release(long staffId, String key) {
		jdbc.sql("DELETE FROM idempotency_key WHERE staff_id = ? AND key = ?").params(staffId, key).update();
	}

	public void deleteCreatedBefore(Instant cutoff) {
		jdbc.sql("DELETE FROM idempotency_key WHERE created_at < ?").param(Timestamp.from(cutoff)).update();
	}

}
