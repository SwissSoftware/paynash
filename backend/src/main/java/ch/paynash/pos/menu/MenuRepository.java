package ch.paynash.pos.menu;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MenuRepository {

	private static final String ITEM_COLUMNS = "id, category_id, name, type, price_rappen, description, display_order, active, image_ref";

	private final JdbcClient jdbc;

	public MenuRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// --- categories

	public List<Category> findAllCategories() {
		return jdbc.sql("SELECT id, name, display_order FROM category ORDER BY display_order, lower(name)")
			.query(this::mapCategory)
			.list();
	}

	public Optional<Category> findCategory(long id) {
		return jdbc.sql("SELECT id, name, display_order FROM category WHERE id = ?")
			.param(id)
			.query(this::mapCategory)
			.optional();
	}

	public boolean categoryNameExists(String name, Long exceptId) {
		return jdbc.sql("SELECT count(*) FROM category WHERE lower(name) = lower(?) AND id <> coalesce(?, -1)")
			.params(name, exceptId)
			.query(Long.class)
			.single() > 0;
	}

	public long insertCategory(String name) {
		return jdbc.sql("""
				INSERT INTO category (name, display_order)
				VALUES (?, (SELECT coalesce(max(display_order), -1) + 1 FROM category)) RETURNING id""")
			.param(name)
			.query(Long.class)
			.single();
	}

	public void renameCategory(long id, String name) {
		jdbc.sql("UPDATE category SET name = ? WHERE id = ?").params(name, id).update();
	}

	public void setCategoryOrder(long id, int order) {
		jdbc.sql("UPDATE category SET display_order = ? WHERE id = ?").params(order, id).update();
	}

	public void deleteCategory(long id) {
		jdbc.sql("DELETE FROM category WHERE id = ?").param(id).update();
	}

	// --- items

	public List<MenuItem> findAllItems() {
		return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM menu_item ORDER BY category_id, display_order, lower(name)")
			.query(this::mapItem)
			.list();
	}

	public List<MenuItem> findItemsInCategory(long categoryId) {
		return jdbc.sql("SELECT " + ITEM_COLUMNS
				+ " FROM menu_item WHERE category_id = ? ORDER BY display_order, lower(name)")
			.param(categoryId)
			.query(this::mapItem)
			.list();
	}

	public Optional<MenuItem> findItem(long id) {
		return jdbc.sql("SELECT " + ITEM_COLUMNS + " FROM menu_item WHERE id = ?").param(id).query(this::mapItem).optional();
	}

	public long insertItem(long categoryId, String name, ItemType type, int priceRappen, String description,
			boolean active) {
		return jdbc.sql("""
				INSERT INTO menu_item (category_id, name, type, price_rappen, description, active, display_order)
				VALUES (?, ?, ?, ?, ?, ?,
				        (SELECT coalesce(max(display_order), -1) + 1 FROM menu_item WHERE category_id = ?))
				RETURNING id""")
			.params(categoryId, name, type.dbValue(), priceRappen, description, active, categoryId)
			.query(Long.class)
			.single();
	}

	public void updateItem(long id, long categoryId, String name, ItemType type, int priceRappen, String description,
			boolean active) {
		jdbc.sql("""
				UPDATE menu_item SET category_id = ?, name = ?, type = ?, price_rappen = ?, description = ?,
				                     active = ?, updated_at = now()
				WHERE id = ?""")
			.params(categoryId, name, type.dbValue(), priceRappen, description, active, id)
			.update();
	}

	public void moveItemToEnd(long id, long categoryId) {
		jdbc.sql("""
				UPDATE menu_item SET display_order =
				    (SELECT coalesce(max(display_order), -1) + 1 FROM menu_item WHERE category_id = ?)
				WHERE id = ?""")
			.params(categoryId, id)
			.update();
	}

	public void setItemOrder(long id, int order) {
		jdbc.sql("UPDATE menu_item SET display_order = ? WHERE id = ?").params(order, id).update();
	}

	public void setImageRef(long id, String imageRef) {
		jdbc.sql("UPDATE menu_item SET image_ref = ?, updated_at = now() WHERE id = ?").params(imageRef, id).update();
	}

	public long countItemsUsingImage(String imageRef) {
		return jdbc.sql("SELECT count(*) FROM menu_item WHERE image_ref = ?")
			.param(imageRef)
			.query(Long.class)
			.single();
	}

	private Category mapCategory(ResultSet rs, int row) throws SQLException {
		return new Category(rs.getLong("id"), rs.getString("name"), rs.getInt("display_order"));
	}

	private MenuItem mapItem(ResultSet rs, int row) throws SQLException {
		return new MenuItem(rs.getLong("id"), rs.getLong("category_id"), rs.getString("name"),
				ItemType.fromDb(rs.getString("type")), rs.getInt("price_rappen"), rs.getString("description"),
				rs.getInt("display_order"), rs.getBoolean("active"), rs.getString("image_ref"));
	}

}
