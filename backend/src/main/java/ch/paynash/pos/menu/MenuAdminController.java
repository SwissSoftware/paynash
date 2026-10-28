package ch.paynash.pos.menu;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.paynash.pos.common.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin: menu")
public class MenuAdminController {

	private final MenuAdminService service;

	public MenuAdminController(MenuAdminService service) {
		this.service = service;
	}

	public record CategoryView(long id, String name, int displayOrder) {

		static CategoryView of(Category c) {
			return new CategoryView(c.id(), c.name(), c.displayOrder());
		}

	}

	public record CategoryRequest(@NotBlank @Size(max = 60) String name) {
	}

	public record OrderRequest(@NotNull List<Long> ids) {
	}

	public record ItemView(long id, long categoryId, String name, ItemType type, String price, String description,
			int displayOrder, boolean active, String imageUrl) {

		static ItemView of(MenuItem i) {
			return new ItemView(i.id(), i.categoryId(), i.name(), i.type(), Money.format(i.priceRappen()),
					i.description(), i.displayOrder(), i.active(), imageUrlFor(i.imageRef()));
		}

	}

	public record ItemRequest(@NotNull Long categoryId, @NotBlank @Size(max = 80) String name,
			@NotNull(message = "Type is required (food or drink)") ItemType type,
			@NotNull(message = "Price is required") String price, @Size(max = 200) String description,
			Boolean active) {

		MenuAdminService.ItemInput toInput() {
			return new MenuAdminService.ItemInput(categoryId, name, type, price, description,
					active == null || active);
		}

	}

	static String imageUrlFor(String ref) {
		return ref == null ? null : "/images/" + ref;
	}

	// --- categories

	@GetMapping("/categories")
	@Operation(summary = "All categories in display order")
	public List<CategoryView> categories() {
		return service.categories().stream().map(CategoryView::of).toList();
	}

	@PostMapping("/categories")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a category (appended at the end)")
	public CategoryView createCategory(@Valid @RequestBody CategoryRequest r) {
		return CategoryView.of(service.createCategory(r.name()));
	}

	@PutMapping("/categories/{id}")
	@Operation(summary = "Rename a category")
	public CategoryView renameCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest r) {
		return CategoryView.of(service.renameCategory(id, r.name()));
	}

	@PutMapping("/categories/order")
	@Operation(summary = "Reorder categories", description = "ids must list every category exactly once.")
	public List<CategoryView> reorderCategories(@Valid @RequestBody OrderRequest r) {
		return service.reorderCategories(r.ids()).stream().map(CategoryView::of).toList();
	}

	@DeleteMapping("/categories/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete an empty category",
			description = "409 with the item names if the category still contains items.")
	public void deleteCategory(@PathVariable long id) {
		service.deleteCategory(id);
	}

	@PutMapping("/categories/{id}/items/order")
	@Operation(summary = "Reorder the items of a category",
			description = "ids must list every item of the category exactly once.")
	public List<ItemView> reorderItems(@PathVariable long id, @Valid @RequestBody OrderRequest r) {
		return service.reorderItems(id, r.ids()).stream().map(ItemView::of).toList();
	}

	// --- items

	@GetMapping("/items")
	@Operation(summary = "All items, including inactive ones")
	public List<ItemView> items() {
		return service.items().stream().map(ItemView::of).toList();
	}

	@PostMapping("/items")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create an item", description = "price is CHF as a string, e.g. \"4.50\".")
	public ItemView createItem(@Valid @RequestBody ItemRequest r) {
		return ItemView.of(service.createItem(r.toInput()));
	}

	@PutMapping("/items/{id}")
	@Operation(summary = "Edit, deactivate, or reactivate an item",
			description = "Items are deactivated instead of deleted, so past orders stay intact.")
	public ItemView updateItem(@PathVariable long id, @Valid @RequestBody ItemRequest r) {
		return ItemView.of(service.updateItem(id, r.toInput()));
	}

	@PostMapping(path = "/items/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Upload or replace the item image",
			description = "JPEG, PNG or WebP up to 5 MB. Stored as WebP, max 512 px.")
	public ItemView uploadImage(@PathVariable long id, @RequestPart("file") MultipartFile file) throws IOException {
		return ItemView.of(service.replaceImage(id, file.getBytes()));
	}

	@DeleteMapping("/items/{id}/image")
	@Operation(summary = "Remove the item image")
	public ItemView removeImage(@PathVariable long id) {
		return ItemView.of(service.removeImage(id));
	}

}
