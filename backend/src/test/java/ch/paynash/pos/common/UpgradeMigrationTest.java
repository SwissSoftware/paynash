package ch.paynash.pos.common;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import ch.paynash.pos.TestcontainersConfiguration;

/**
 * Upgrades a database that already holds data, as a development database does. The other tests only ever migrate an
 * empty database. Runs in its own schema so the application's schema stays untouched.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UpgradeMigrationTest {

	private static final String SCHEMA = "upgrade_test";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@Test
	void orderLifecycleMigrationRemovesOldOrdersWithRoundsAndLines() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("5").migrate();
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO category (id, name) OVERRIDING SYSTEM VALUE VALUES (1, 'Drinks');
				INSERT INTO menu_item (id, category_id, name, type, price_rappen) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 'Cola', 'drink', 450);
				INSERT INTO orders (id, table_label, opened_by, opened_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, '12', 1, now());
				INSERT INTO order_round (id, order_id, number, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 1, 1, now());
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				    unit_price_snapshot, qty, status, round_id, added_by, added_at)
				    VALUES (1, 1, 'Cola', 'drink', 'Drinks', 450, 1, 'sent', 1, 1, now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(jdbc.sql("SELECT count(*) FROM " + SCHEMA + ".orders").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM " + SCHEMA + ".menu_item").query(Long.class).single()).isOne();
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	private Flyway flyway(String target) {
		var config = Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA);
		if (target != null) {
			config.target(target);
		}
		return config.load();
	}

	private void in(String schema, String sql) {
		jdbc.sql("SET search_path TO " + schema + "; " + sql + " SET search_path TO public").update();
	}

}
