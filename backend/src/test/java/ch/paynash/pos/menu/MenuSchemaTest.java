package ch.paynash.pos.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import ch.paynash.pos.support.IntegrationTest;

class MenuSchemaTest extends IntegrationTest {

	@Test
	void tablesHaveExpectedColumns() {
		assertThat(columns("category")).contains("id", "name", "display_order");
		assertThat(columns("menu_item")).contains("id", "category_id", "name", "type", "price_rappen",
				"description", "display_order", "active", "image_ref");
	}

	@Test
	void typeMustBeFoodOrDrink() {
		long cat = category("Drinks");
		assertThatThrownBy(() -> item(cat, "x", "dessert", 100)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void priceCannotBeNegative() {
		long cat = category("Drinks");
		assertThatThrownBy(() -> item(cat, "x", "drink", -1)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void categoryWithItemsCannotBeDeletedAtDbLevel() {
		long cat = category("Drinks");
		item(cat, "Cola", "drink", 450);
		assertThatThrownBy(() -> jdbc.sql("DELETE FROM category WHERE id = ?").param(cat).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long category(String name) {
		return jdbc.sql("INSERT INTO category (name) VALUES (?) RETURNING id").param(name).query(Long.class).single();
	}

	private void item(long categoryId, String name, String type, int price) {
		jdbc.sql("INSERT INTO menu_item (category_id, name, type, price_rappen) VALUES (?, ?, ?, ?)")
			.params(categoryId, name, type, price)
			.update();
	}

	private List<String> columns(String table) {
		return jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = ?")
			.param(table)
			.query(String.class)
			.list();
	}

}
