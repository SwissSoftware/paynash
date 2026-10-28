package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class OrderSchemaTest extends IntegrationTest {

	private long waiter;

	@BeforeEach
	void setUp() {
		waiter = createStaff("anna", Role.WAITER, "4711");
	}

	@Test
	void tablesHaveExpectedColumns() {
		assertThat(columns("orders")).contains("id", "table_label", "status", "opened_by", "opened_at", "version");
		assertThat(columns("order_round")).contains("id", "order_id", "number", "sent_by", "sent_at");
		assertThat(columns("order_line")).contains("id", "order_id", "menu_item_id", "name_snapshot",
				"type_snapshot", "category_snapshot", "unit_price_snapshot", "qty", "note", "status", "round_id",
				"cancel_reason", "version");
	}

	@Test
	void secondOpenOrderForSameTableFailsAtDbLevel() {
		order("12", "open");
		assertThatThrownBy(() -> order("12", "open")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void closedOrdersDoNotBlockANewOpenOrder() {
		order("12", "paid");
		order("12", "voided");
		order("12", "open");
		order("13", "open");
	}

	@Test
	void statusMustBeKnown() {
		assertThatThrownBy(() -> order("12", "closed")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void quantityMustBeBetween1And99() {
		long order = order("12", "open");
		long item = menuItem();
		line(order, item, 1);
		line(order, item, 99);
		assertThatThrownBy(() -> line(order, item, 0)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> line(order, item, 100)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sentLineNeedsARound() {
		long order = order("12", "open");
		long line = line(order, menuItem(), 1);
		assertThatThrownBy(() -> jdbc.sql("UPDATE order_line SET status = 'sent' WHERE id = ?").param(line).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long order(String table, String status) {
		return jdbc.sql("INSERT INTO orders (table_label, status, opened_by, opened_at) VALUES (?, ?, ?, ?) RETURNING id")
			.params(table, status, waiter, Timestamp.from(Instant.now()))
			.query(Long.class)
			.single();
	}

	private long menuItem() {
		long category = jdbc.sql("INSERT INTO category (name) VALUES ('Drinks') ON CONFLICT DO NOTHING RETURNING id")
			.query(Long.class)
			.optional()
			.orElseGet(() -> jdbc.sql("SELECT id FROM category").query(Long.class).single());
		return jdbc.sql("INSERT INTO menu_item (category_id, name, type, price_rappen) VALUES (?, 'Cola', 'drink', 450) RETURNING id")
			.param(category)
			.query(Long.class)
			.single();
	}

	private long line(long order, long item, int qty) {
		return jdbc.sql("""
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				                        unit_price_snapshot, qty, added_by, added_at)
				VALUES (?, ?, 'Cola', 'drink', 'Drinks', 450, ?, ?, ?) RETURNING id""")
			.params(order, item, qty, waiter, Timestamp.from(Instant.now()))
			.query(Long.class)
			.single();
	}

	private List<String> columns(String table) {
		return jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = ?")
			.param(table)
			.query(String.class)
			.list();
	}

}
