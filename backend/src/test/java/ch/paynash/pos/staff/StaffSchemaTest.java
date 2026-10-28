package ch.paynash.pos.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import ch.paynash.pos.support.IntegrationTest;

class StaffSchemaTest extends IntegrationTest {

	@Test
	void staffAndSessionTablesHaveExpectedColumns() {
		assertThat(columns("staff")).contains("id", "username", "display_name", "role", "active", "pin_hash",
				"failed_attempts", "locked_until");
		assertThat(columns("session")).contains("token_hash", "staff_id", "created_at", "expires_at");
	}

	@Test
	void usernameIsUniqueIgnoringCase() {
		createStaff("anna", Role.WAITER, "4711");
		assertThatThrownBy(() -> createStaff("ANNA", Role.WAITER, "4711")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void roleIsRestricted() {
		assertThatThrownBy(() -> jdbc
			.sql("INSERT INTO staff (username, display_name, role, pin_hash) VALUES ('x', 'X', 'chef', 'h')")
			.update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingStaffDeletesTheirSessions() {
		long id = createStaff("anna", Role.WAITER, "4711");
		jdbc.sql("INSERT INTO session VALUES (repeat('a', 64), ?, now(), now() + interval '1 hour')").param(id).update();
		jdbc.sql("DELETE FROM staff WHERE id = ?").param(id).update();
		assertThat(jdbc.sql("SELECT count(*) FROM session").query(Long.class).single()).isZero();
	}

	private List<String> columns(String table) {
		return jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = ?")
			.param(table)
			.query(String.class)
			.list();
	}

}
