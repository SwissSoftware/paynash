package ch.paynash.pos.menu;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.common.Money;

@Service
public class MenuAdminService {

	private final MenuRepository menu;

	private final ImageStore images;

	public MenuAdminService(MenuRepository menu, ImageStore images) {
		this.menu = menu;
		this.images = images;
	}

	// --- categories

	public List<Category> categories() {
		return menu.findAllCategories();
	}

	@Transactional
	public Category createCategory(String name) {
		String trimmed = name.trim();
		requireUniqueCategoryName(trimmed, null);
		try {
			return category(menu.insertCategory(trimmed));
		}
		catch (DuplicateKeyException ex) {
			throw categoryNameTaken();
		}
	}

	@Transactional
	public Category renameCategory(long id, String name) {
		category(id);
		String trimmed = name.trim();
		requireUniqueCategoryName(trimmed, id);
		menu.renameCategory(id, trimmed);
		return category(id);
	}

	/** {@code ids} must list every category exactly once, in the new order. */
	@Transactional
	public List<Category> reorderCategories(List<Long> ids) {
		Set<Long> existing = new HashSet<>(menu.findAllCategories().stream().map(Category::id).toList());
		requireSameIds(existing, ids);
		for (int i = 0; i < ids.size(); i++) {
			menu.setCategoryOrder(ids.get(i), i);
		}
		return menu.findAllCategories();
	}

	@Transactional
	public void deleteCategory(long id) {
		category(id);
		List<String> names = menu.findItemsInCategory(id).stream().map(MenuItem::name).toList();
		if (!names.isEmpty()) {
			throw ApiException.conflict("The category still contains items: " + String.join(", ", names))
				.with("items", names);
		}
		menu.deleteCategory(id);
	}

	// --- items

	public List<MenuItem> items() {
		return menu.findAllItems();
	}

	public record ItemInput(long categoryId, String name, ItemType type, String price, String description,
			boolean active) {
	}

	@Transactional
	public MenuItem createItem(ItemInput in) {
		category(in.categoryId(), "categoryId");
		long id = menu.insertItem(in.categoryId(), in.name().trim(), in.type(), price(in.price()),
				blankToNull(in.description()), in.active());
		return item(id);
	}

	@Transactional
	public MenuItem updateItem(long id, ItemInput in) {
		MenuItem current = item(id);
		category(in.categoryId(), "categoryId");
		menu.updateItem(id, in.categoryId(), in.name().trim(), in.type(), price(in.price()),
				blankToNull(in.description()), in.active());
		if (current.categoryId() != in.categoryId()) {
			menu.moveItemToEnd(id, in.categoryId());
		}
		return item(id);
	}

	@Transactional
	public List<MenuItem> reorderItems(long categoryId, List<Long> ids) {
		category(categoryId);
		Set<Long> existing = new HashSet<>(menu.findItemsInCategory(categoryId).stream().map(MenuItem::id).toList());
		requireSameIds(existing, ids);
		for (int i = 0; i < ids.size(); i++) {
			menu.setItemOrder(ids.get(i), i);
		}
		return menu.findItemsInCategory(categoryId);
	}

	/**
	 * The new image is fully validated and stored before the item changes, so a failed upload leaves the old image
	 * in place.
	 */
	@Transactional
	public MenuItem replaceImage(long id, byte[] upload) {
		MenuItem current = item(id);
		String ref = images.store(upload);
		menu.setImageRef(id, ref);
		deleteAfterCommitIfUnused(current.imageRef(), ref);
		return item(id);
	}

	@Transactional
	public MenuItem removeImage(long id) {
		MenuItem current = item(id);
		menu.setImageRef(id, null);
		deleteAfterCommitIfUnused(current.imageRef(), null);
		return item(id);
	}

	private void deleteAfterCommitIfUnused(String oldRef, String newRef) {
		if (oldRef == null || oldRef.equals(newRef)) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				if (menu.countItemsUsingImage(oldRef) == 0) {
					images.delete(oldRef);
				}
			}
		});
	}

	// --- helpers

	private Category category(long id) {
		return menu.findCategory(id).orElseThrow(() -> ApiException.notFound("Category"));
	}

	private void category(long id, String field) {
		if (menu.findCategory(id).isEmpty()) {
			throw ApiException.validation(field, "Category does not exist");
		}
	}

	private MenuItem item(long id) {
		return menu.findItem(id).orElseThrow(() -> ApiException.notFound("Menu item"));
	}

	private void requireUniqueCategoryName(String name, Long exceptId) {
		if (menu.categoryNameExists(name, exceptId)) {
			throw categoryNameTaken();
		}
	}

	private static ApiException categoryNameTaken() {
		return ApiException.validation("name", "A category with this name already exists");
	}

	private static void requireSameIds(Set<Long> existing, List<Long> ids) {
		if (ids.size() != existing.size() || !existing.equals(new HashSet<>(ids))) {
			throw ApiException.validation("ids", "Must list every entry exactly once");
		}
	}

	private static int price(String value) {
		Integer rappen = Money.parseRappen(value);
		if (rappen == null) {
			throw ApiException.validation("price", "Price must be at least 0.00 with at most two decimals");
		}
		return rappen;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
