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
import ch.paynash.pos.common.VatRates;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.SettingsService;

@Service
public class MenuAdminService {

	private final MenuRepository menu;

	private final ImageStore images;

	private final SettingsService settings;

	public MenuAdminService(MenuRepository menu, ImageStore images, SettingsService settings) {
		this.menu = menu;
		this.images = images;
		this.settings = settings;
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

	/**
	 * {@code vatRate} is a percentage such as "8.1"; null keeps the current rate (8.1 % for new items), because the
	 * apps only show it while VAT is on (spec menu-catalog, "VAT rate per item").
	 */
	public record ItemInput(long categoryId, String name, ItemType type, String price, String description,
			boolean active, Integer durationMin, String vatRate) {
	}

	@Transactional
	public MenuItem createItem(ItemInput in) {
		category(in.categoryId(), "categoryId");
		requireTypeForMode(in.type());
		long id = menu.insertItem(in.categoryId(), in.name().trim(), in.type(), price(in.price()),
				blankToNull(in.description()), in.active(), duration(in), vatRate(in.vatRate(), VatRates.STANDARD));
		return item(id);
	}

	@Transactional
	public MenuItem updateItem(long id, ItemInput in) {
		MenuItem current = item(id);
		category(in.categoryId(), "categoryId");
		requireTypeForMode(in.type());
		menu.updateItem(id, in.categoryId(), in.name().trim(), in.type(), price(in.price()),
				blankToNull(in.description()), in.active(), duration(in),
				vatRate(in.vatRate(), current.vatRatePermille()));
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

	/** Salon mode sells only services, event mode never does (spec menu-catalog, "Item types per mode"). */
	private void requireTypeForMode(ItemType type) {
		boolean salon = settings.mode() == Mode.SALON;
		if (salon && type != ItemType.SERVICE) {
			throw ApiException.validation("type", "Only services can be created in salon mode");
		}
		if (!salon && type == ItemType.SERVICE) {
			throw ApiException.validation("type", "Services are only available in salon mode");
		}
	}

	private static Integer duration(ItemInput in) {
		Integer minutes = in.durationMin();
		if (minutes == null) {
			return null;
		}
		if (in.type() != ItemType.SERVICE) {
			throw ApiException.validation("durationMin", "Only services have a duration");
		}
		if (minutes < 5 || minutes > 600) {
			throw ApiException.validation("durationMin", "Duration must be between 5 and 600 minutes");
		}
		return minutes;
	}

	private static int vatRate(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		Integer permille = VatRates.parse(value);
		if (permille == null) {
			throw ApiException.validation("vatRate", "VAT rate must be one of 8.1, 2.6, 3.8, or 0");
		}
		return permille;
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
