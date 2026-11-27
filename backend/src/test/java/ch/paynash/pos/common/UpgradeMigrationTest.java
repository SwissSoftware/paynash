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

	@Test
	void printHardeningMigrationKeepsFailedTicketsAndAcceptsNewStatesAndStations() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("8").migrate();
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO orders (id, table_label, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, '12', 1, now());
				INSERT INTO printer (id, name, address, item_type) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'Bar', '10.0.0.5', 'drink');
				INSERT INTO print_job (order_id, item_type, printer_id, kind, content, status, last_error, created_at)
				    VALUES (1, 'drink', 1, 'normal', 'x', 'failed', 'printer offline', now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(jdbc.sql("SELECT status FROM " + SCHEMA + ".print_job").query(String.class).single())
			.isEqualTo("failed");
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (2, 'station-bar', 'Station Bar', 'station', 'h');
				INSERT INTO station_printer (station_id, printer_id) VALUES (2, 1);
				INSERT INTO print_job (order_id, printer_id, kind, content, status, ticket_no, created_at)
				    VALUES (1, 1, 'normal', 'x', 'printing', 1, now()),
				           (1, 1, 'normal', 'x', 'unclear', 2, now());
				""");
		assertThat(jdbc.sql("SELECT count(*) FROM " + SCHEMA + ".print_job").query(Long.class).single()).isEqualTo(3);
		assertThat(jdbc.sql("SELECT last_ticket_no FROM " + SCHEMA + ".printer").query(Integer.class).single())
			.isZero();
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	@Test
	void salonMigrationsKeepExistingEventData() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("9").migrate();
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO category (id, name) OVERRIDING SYSTEM VALUE VALUES (1, 'Drinks');
				INSERT INTO menu_item (id, category_id, name, type, price_rappen) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 'Cola', 'drink', 450);
				INSERT INTO orders (id, table_label, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, '12', 1, now());
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				    unit_price_snapshot, qty) VALUES (1, 1, 'Cola', 'drink', 'Drinks', 450, 2);
				INSERT INTO printer (id, name, address, item_type) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'Bar', '10.0.0.5', 'drink');
				INSERT INTO print_job (order_id, item_type, printer_id, kind, content, status, ticket_no, created_at)
				    VALUES (1, 'drink', 1, 'normal', 'x', 'printed', 1, now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(one("SELECT template FROM installation_settings")).isEqualTo("event");
		assertThat(one("SELECT vat_rate_permille::text FROM menu_item")).isEqualTo("81");
		assertThat(one("SELECT vat_rate_snapshot::text FROM order_line")).isEqualTo("81");
		assertThat(one("SELECT table_label FROM orders")).isEqualTo("12");
		assertThat(one("SELECT prints_tickets::text FROM printer")).isEqualTo("true");
		assertThat(one("SELECT status FROM print_job")).isEqualTo("printed");
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	@Test
	void printerCategoryMigrationKeepsTodaysRouting() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("14").migrate();
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO category (id, name) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'Pizza'), (2, 'Drinks'), (3, 'Mixed'), (4, 'Face'), (5, 'Empty');
				INSERT INTO menu_item (id, category_id, name, type, price_rappen) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 'Margherita', 'food', 1800), (2, 2, 'Cola', 'drink', 450),
				           (3, 3, 'Nachos', 'food', 900), (4, 3, 'Beer', 'drink', 600),
				           (5, 4, 'Facial', 'service', 12000);
				INSERT INTO printer (id, name, address, purpose) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'Kitchen', '10.0.0.5', 'food'), (2, 'Bar', '10.0.0.6', 'drink'),
				           (3, 'Counter', '10.0.0.7', 'receipt');
				INSERT INTO orders (id, table_label, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, '12', 1, now()), (2, '13', 1, now());
				INSERT INTO order_line (id, order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				    unit_price_snapshot, qty) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 1, 'Margherita', 'food', 'Pizza', 1800, 1),
				           (2, 1, 2, 'Cola', 'drink', 'Drinks', 450, 1),
				           (3, 2, 1, 'Margherita', 'food', 'Pizza', 1800, 1);
				INSERT INTO print_job (order_id, item_type, printer_id, kind, content, status, ticket_no, created_at)
				    VALUES (1, 'food', 1, 'normal', 'x', 'printed', 1, now()),
				           (1, 'drink', 2, 'normal', 'x', 'printed', 1, now());
				INSERT INTO print_job (order_id, item_type, kind, content, status, last_error, created_at)
				    VALUES (2, 'food', 'normal', 'x', 'failed', 'no printer configured', now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(rows("SELECT name || ':' || prints_tickets || ':' || prints_receipts FROM printer ORDER BY id"))
			.containsExactly("Kitchen:true:false", "Bar:true:false", "Counter:false:true");
		assertThat(rows("""
				SELECT p.name || ':' || c.name FROM printer_category pc JOIN printer p ON p.id = pc.printer_id
				JOIN category c ON c.id = pc.category_id ORDER BY p.id, c.id"""))
			.containsExactly("Kitchen:Pizza", "Kitchen:Mixed", "Bar:Drinks", "Bar:Mixed");
		assertThat(rows("SELECT order_line_id || ':' || printer_id FROM order_line_printer ORDER BY order_line_id"))
			.containsExactly("1:1", "2:2");
		assertThat(rows("SELECT count(*)::text FROM print_job")).containsExactly("3");
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	/** Spec business-features, "Existing installations keep their behaviour": event installation. */
	@Test
	void businessFeaturesMigrationTurnsEventModeIntoEventTemplate() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("15").migrate();
		in(SCHEMA, """
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO dining_table (number, name) VALUES ('12', 'Terrace');
				INSERT INTO orders (id, table_label, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, '12', 1, now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(one("""
				SELECT template || ':' || feature_tables || ':' || feature_stations || ':' || feature_customers || ':'
				    || feature_invoices || ':' || feature_sales_report FROM installation_settings"""))
			.isEqualTo("event:true:true:false:false:false");
		assertThat(one("SELECT term_waiter || '/' || term_items || '/' || term_send FROM installation_settings"))
			.isEqualTo("Waiter/Menu/Send");
		assertThat(one("SELECT table_label FROM orders")).isEqualTo("12");
		assertThat(rows("SELECT number FROM dining_table")).containsExactly("12");
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	/** Spec business-features, "Existing installations keep their behaviour": salon installation. */
	@Test
	void businessFeaturesMigrationTurnsSalonModeIntoSalonTemplateAndRelaxesOrderTarget() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("15").migrate();
		in(SCHEMA, """
				UPDATE installation_settings SET mode = 'salon', business_name = 'Aurora';
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO customer (id, first_name, last_name) OVERRIDING SYSTEM VALUE VALUES (1, 'Laura', 'Meier');
				INSERT INTO orders (id, customer_id, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 1, now());
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(one("""
				SELECT template || ':' || feature_tables || ':' || feature_stations || ':' || feature_customers || ':'
				    || feature_invoices || ':' || feature_sales_report FROM installation_settings"""))
			.isEqualTo("salon:false:false:true:true:true");
		assertThat(one("SELECT term_waiter || '/' || term_items || '/' || term_send FROM installation_settings"))
			.isEqualTo("Staff/Services/Confirm");
		assertThat(one("SELECT business_name FROM installation_settings")).isEqualTo("Aurora");
		assertThat(one("SELECT customer_id::text FROM orders")).isEqualTo("1");
		// Counter sales and table orders with a customer are now possible (design D3).
		in(SCHEMA, """
				INSERT INTO orders (id, sent_by, sent_at) OVERRIDING SYSTEM VALUE VALUES (2, 1, now());
				INSERT INTO orders (id, table_label, customer_id, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (3, '5', 1, 1, now());
				""");
		assertThat(rows("SELECT count(*)::text FROM orders")).containsExactly("3");
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	/** Change invoices-without-customer, design D1: existing invoices stay, new ones may have no customer. */
	@Test
	void invoicesWithoutCustomerMigrationKeepsInvoicesAndAllowsNoCustomer() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
		flyway("16").migrate();
		in(SCHEMA, """
				UPDATE installation_settings SET template = 'salon', feature_tables = FALSE, feature_stations = FALSE,
				    feature_customers = TRUE, feature_invoices = TRUE, feature_sales_report = TRUE;
				INSERT INTO staff (id, username, display_name, role, pin_hash) OVERRIDING SYSTEM VALUE
				    VALUES (1, 'anna', 'Anna', 'waiter', 'h');
				INSERT INTO customer (id, first_name, last_name) OVERRIDING SYSTEM VALUE VALUES (1, 'Laura', 'Meier');
				INSERT INTO orders (id, customer_id, sent_by, sent_at) OVERRIDING SYSTEM VALUE
				    VALUES (1, 1, 1, now()), (2, NULL, 1, now());
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen)
				    VALUES ('2026-0001', 2026, 1, 1, 1, DATE '2026-11-27', now(), 1, '{}', '{"name":"Laura Meier"}',
				            false, 12000);
				""");

		var result = flyway(null).migrate();

		assertThat(result.success).isTrue();
		assertThat(one("SELECT customer_id || ':' || (customer->>'name') FROM invoice")).isEqualTo("1:Laura Meier");
		in(SCHEMA, """
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen)
				    VALUES ('2026-0002', 2026, 2, 2, NULL, DATE '2026-11-27', now(), 1, '{}', NULL, false, 900);
				UPDATE installation_settings SET feature_customers = FALSE;
				""");
		assertThat(rows("SELECT count(*)::text FROM invoice WHERE customer_id IS NULL")).containsExactly("1");
		assertThat(one("SELECT feature_invoices || ':' || feature_customers FROM installation_settings"))
			.isEqualTo("true:false");
		// A copy without customer id (or the other way round) is still rejected.
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> in(SCHEMA, """
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen)
				    VALUES ('2026-0003', 2026, 3, 2, NULL, DATE '2026-11-27', now(), 1, '{}', '{"name":"x"}', false, 1);
				""")).hasMessageContaining("invoice_customer_copy_check");
		jdbc.sql("SET search_path TO public").update();
		jdbc.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
	}

	private java.util.List<String> rows(String sql) {
		return jdbc.sql(sql.replaceAll("\\b(FROM|JOIN) (\\w+)", "$1 " + SCHEMA + ".$2")).query(String.class).list();
	}

	private String one(String sql) {
		return jdbc.sql(sql.replace(" FROM ", " FROM " + SCHEMA + ".")).query(String.class).single();
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
