package ch.paynash.pos.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import ch.paynash.pos.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void baselineMigrationAppliesToEmptyDatabase() {
		var applied = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
			.query(String.class)
			.list();
		assertThat(applied).contains("1");
	}

}
