package ch.paynash.pos.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import ch.paynash.pos.common.Money;
import ch.paynash.pos.common.VatRates;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The menu as waiters see it: active items only, grouped by category, in display order. */
@RestController
@Tag(name = "Menu")
public class WaiterMenuController {

	private final MenuRepository menu;

	private final SettingsService settings;

	public WaiterMenuController(MenuRepository menu, SettingsService settings) {
		this.menu = menu;
		this.settings = settings;
	}

	/** {@code durationMin} is only set for services; {@code vatRate} is a percentage such as "8.1". */
	public record MenuItemView(long id, String name, ItemType type, String price, String description,
			String imageUrl, Integer durationMin, String vatRate) {
	}

	public record MenuCategoryView(long id, String name, List<MenuItemView> items) {
	}

	public record MenuView(List<MenuCategoryView> categories) {
	}

	@GetMapping("/menu")
	@Operation(summary = "Active menu for order taking",
			description = "Supports ETag / If-None-Match: answers 304 when the menu has not changed.")
	public ResponseEntity<MenuView> menu() {
		// Salon mode offers only services, event mode never does (spec menu-catalog, "Item types per mode").
		boolean salon = settings.mode() == Mode.SALON;
		Map<Long, List<MenuItem>> byCategory = menu.findAllItems()
			.stream()
			.filter(MenuItem::active)
			.filter(i -> (i.type() == ItemType.SERVICE) == salon)
			.collect(Collectors.groupingBy(MenuItem::categoryId));
		List<MenuCategoryView> categories = new ArrayList<>();
		for (Category c : menu.findAllCategories()) {
			List<MenuItemView> items = byCategory.getOrDefault(c.id(), List.of())
				.stream()
				.map(i -> new MenuItemView(i.id(), i.name(), i.type(), Money.format(i.priceRappen()),
						i.description(), MenuAdminController.imageUrlFor(i.imageRef()), i.durationMin(),
						VatRates.format(i.vatRatePermille())))
				.toList();
			if (!items.isEmpty()) {
				categories.add(new MenuCategoryView(c.id(), c.name(), items));
			}
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new MenuView(categories));
	}

	@Bean
	static FilterRegistrationBean<ShallowEtagHeaderFilter> menuEtagFilter() {
		var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/menu");
		return registration;
	}

}
