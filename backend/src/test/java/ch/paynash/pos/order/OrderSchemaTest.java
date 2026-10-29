package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

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
		assertThat(columns("orders")).contains("id", "table_label", "status", "sent_by", "sent_at", "version")
			.doesNotContain("opened_by", "opened_at");
		assertThat(columns("order_line")).contains("id", "order_id", "menu_item_id", "name_snapshot",
				"type_snapshot", "category_snapshot", "unit_price_snapshot", "qty", "note", "status",
				"cancel_reason")
			.doesNotContain("round_id", "version");
		assertThat(columns("order_round")).isEmpty();
	}

	@Test
	void aTableCanHaveSeveralSentOrders() {
		order("12", "sent");
		order("12", "sent");
		order("12", "voided");
	}

	@Test
	void orderStatusMustBeSentOrVoided() {
		assertThatThrownBy(() -> order("12", "open")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> order("12", "paid")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void lineStatusMustBeSentOrCancelled() {
		long order = order("12", "sent");
		long item = menuItem();
		assertThat(line(order, item, 1, "sent")).isPositive();
		assertThatThrownBy(() -> line(order, item, 1, "unsent")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void quantityMustBeBetween1And99() {
		long order = order("12", "sent");
		long item = menuItem();
		line(order, item, 1, "sent");
		line(order, item, 99, "sent");
		assertThatThrownBy(() -> line(order, item, 0, "sent")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> line(order, item, 100, "sent")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void cancelledLineNeedsAReason() {
		long order = order("12", "sent");
		long line = line(order, menuItem(), 1, "sent");
		assertThatThrownBy(
				() -> jdbc.sql("UPDATE order_line SET status = 'cancelled' WHERE id = ?").param(line).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long order(String table, String status) {
		return jdbc.sql("INSERT INTO orders (table_label, status, sent_by, sent_at) VALUES (?, ?, ?, ?) RETURNING id")
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

	private long line(long order, long item, int qty, String status) {
		return jdbc.sql("""
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				                        unit_price_snapshot, qty, status)
				VALUES (?, ?, 'Cola', 'drink', 'Drinks', 450, ?, ?) RETURNING id""")
			.params(order, item, qty, status)
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
