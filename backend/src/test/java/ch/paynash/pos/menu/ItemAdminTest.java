package ch.paynash.pos.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.settings.Template;
import ch.paynash.pos.staff.Role;

class ItemAdminTest extends MenuTestSupport {

	private long hotDrinks;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		hotDrinks = createCategory("Hot drinks");
	}

	@Test
	void createDrinkItem() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Espresso", "type", "drink", "price", "4.50", "description",
				"Double shot"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Espresso"))
			.andExpect(jsonPath("$.type").value("drink"))
			.andExpect(jsonPath("$.price").value("4.50"))
			.andExpect(jsonPath("$.categoryId").value(hotDrinks))
			.andExpect(jsonPath("$.description").value("Double shot"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.imageUrl").doesNotExist());
	}

	@Test
	void priceIsNormalisedToTwoDecimals() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Tea", "type", "drink", "price", "4.5"))
			.andExpect(jsonPath("$.price").value("4.50"));
		postItem(json("categoryId", hotDrinks, "name", "Water", "type", "drink", "price", "0"))
			.andExpect(jsonPath("$.price").value("0.00"));
	}

	@Test
	void negativePriceIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "-1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").value("Price must be at least 0.00 with at most two decimals"));
	}

	@Test
	void moreThanTwoDecimalsIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "4.505"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void missingPriceIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").value("Price is required"));
	}

	@Test
	void missingTypeIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "price", "1.00")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.type").value("Type is required (food, drink, or service)"));
	}

	@Test
	void unknownTypeIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "dessert", "price", "1.00"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void nameIsRequiredAndAtMost80Characters() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", " ", "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());
		postItem(json("categoryId", hotDrinks, "name", "x".repeat(81), "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());
		postItem(json("categoryId", hotDrinks, "name", "x".repeat(80), "type", "drink", "price", "1.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void descriptionIsAtMost200Characters() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "1.00", "description",
				"d".repeat(201)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.description").exists());
	}

	@Test
	void unknownCategoryIsRejected() throws Exception {
		postItem(json("categoryId", 999999, "name", "X", "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.categoryId").value("Category does not exist"));
	}

	@Test
	void deactivateAndReactivate() throws Exception {
		long id = createItem(hotDrinks, "Tiramisu", "food", "9.00");
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", true))
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void deleteDrinkThatWasNeverOrderedRemovesItAndItsImage() throws Exception {
		long id = createItem(hotDrinks, "Espreso", "drink", "4.50");
		String url = uploadImage(id);
		mvc.perform(get(url)).andExpect(status().isOk());

		deleteItem(id, admin).andExpect(status().isNoContent());

		mvc.perform(get("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		mvc.perform(get(url)).andExpect(status().isNotFound());
		deleteItem(id, admin).andExpect(status().isNotFound());
	}

	@Test
	void deleteServiceThatWasNeverOrdered() throws Exception {
		long face = createCategory("Face");
		long id = createItem(face, "Test service", "service", "50.00");
		deleteItem(id, admin).andExpect(status().isNoContent());
		assertThat(jdbc.sql("SELECT count(*) FROM menu_item WHERE id = ?").param(id).query(Long.class).single())
			.isZero();
	}

	@Test
	void sharedImageStaysWhileAnotherItemUsesIt() throws Exception {
		long a = createItem(hotDrinks, "Espresso", "drink", "4.50");
		long b = createItem(hotDrinks, "Ristretto", "drink", "4.50");
		String url = uploadImage(a);
		jdbc.sql("UPDATE menu_item SET image_ref = (SELECT image_ref FROM menu_item WHERE id = ?) WHERE id = ?")
			.params(a, b)
			.update();

		deleteItem(a, admin).andExpect(status().isNoContent());

		mvc.perform(get(url)).andExpect(status().isOk());
	}

	@Test
	void deleteOrderedItemIsRejectedAndChangesNothing() throws Exception {
		long id = createItem(hotDrinks, "Margherita", "food", "18.00");
		long order = sendOrder(id);

		deleteItem(id, admin).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("Margherita was already ordered and cannot be deleted. Deactivate it instead."))
			.andExpect(jsonPath("$.alreadyOrdered").value(true));

		mvc.perform(get("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$[0].name").value("Margherita"))
			.andExpect(jsonPath("$[0].active").value(true));
		assertThat(jdbc.sql("SELECT count(*) FROM order_line WHERE order_id = ?").param(order).query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void itemOrderedOnlyInAVoidedOrderCannotBeDeleted() throws Exception {
		long id = createItem(hotDrinks, "Tea", "drink", "4.00");
		long order = sendOrder(id);
		mvc.perform(post("/admin/orders/" + order + "/void").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("reason", "test order"))).andExpect(status().isOk());

		deleteItem(id, admin).andExpect(status().isConflict()).andExpect(jsonPath("$.alreadyOrdered").value(true));
	}

	@Test
	void waiterCannotDeleteItems() throws Exception {
		long id = createItem(hotDrinks, "Tea", "drink", "4.00");
		deleteItem(id, waiterToken()).andExpect(status().isForbidden());
	}

	@Test
	void editChangesPriceAndMovesBetweenCategories() throws Exception {
		long cold = createCategory("Cold drinks");
		createItem(cold, "Cola", "drink", "4.50");
		long id = createItem(hotDrinks, "Ice tea", "drink", "4.00");
		update(id, json("categoryId", cold, "name", "Ice tea", "type", "drink", "price", "5.00", "active", true))
			.andExpect(jsonPath("$.price").value("5.00"))
			.andExpect(jsonPath("$.categoryId").value(cold))
			.andExpect(jsonPath("$.displayOrder").value(1));
	}

	@Test
	void reorderItemsWithinCategory() throws Exception {
		long a = createItem(hotDrinks, "A", "drink", "1.00");
		long b = createItem(hotDrinks, "B", "drink", "1.00");
		mvc.perform(put("/admin/categories/" + hotDrinks + "/items/order")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[%d,%d]}".formatted(b, a)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("B", "A")));
	}

	@Test
	void adminListIncludesInactiveItems() throws Exception {
		long id = createItem(hotDrinks, "Tiramisu", "food", "9.00");
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", false));
		mvc.perform(get("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$[0].name").value("Tiramisu"))
			.andExpect(jsonPath("$[0].active").value(false));
	}

	@Test
	void waiterCannotManageItems() throws Exception {
		String waiter = waiterToken();
		mvc.perform(post("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(waiter))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "1.00")))
			.andExpect(status().isForbidden());
	}

	@Test
	void createServiceInSalonMode() throws Exception {
		settingsService.applyTemplate(Template.SALON);
		long face = createCategory("Face");
		postItem(json("categoryId", face, "name", "Facial", "type", "service", "price", "120.00", "durationMin", 60))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("service"))
			.andExpect(jsonPath("$.durationMin").value(60))
			.andExpect(jsonPath("$.vatRate").value("8.1"));
	}

	@Test
	void foodIsAcceptedInASalon() throws Exception {
		// Spec menu-catalog, "Hair salon with drinks": item types no longer depend on a mode.
		settingsService.applyTemplate(Template.SALON);
		postItem(json("categoryId", hotDrinks, "name", "Espresso", "type", "drink", "price", "4.50"))
			.andExpect(status().isCreated());
	}

	@Test
	void serviceIsAcceptedAtAnEvent() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Facial", "type", "service", "price", "120.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void invalidDurationIsRejected() throws Exception {
		settingsService.applyTemplate(Template.SALON);
		for (int minutes : new int[] { 0, 900 }) {
			postItem(json("categoryId", hotDrinks, "name", "Facial", "type", "service", "price", "120.00",
					"durationMin", minutes))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.durationMin").value("Duration must be between 5 and 600 minutes"));
		}
	}

	@Test
	void vatRateDefaultsTo81AndOnlyAllowedRatesAreAccepted() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Tea", "type", "drink", "price", "4.00", "vatRate", "2.6"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.vatRate").value("2.6"));
		postItem(json("categoryId", hotDrinks, "name", "Cola", "type", "drink", "price", "4.00", "vatRate", "7.7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.vatRate").value("VAT rate must be one of 8.1, 2.6, 3.8, or 0"));
	}

	@Test
	void editWithoutVatRateKeepsTheCurrentRate() throws Exception {
		long tea = createItem(hotDrinks, "Tea", "drink", "4.00");
		update(tea, json("categoryId", hotDrinks, "name", "Tea", "type", "drink", "price", "4.00", "vatRate", "0"))
			.andExpect(jsonPath("$.vatRate").value("0.0"));
		update(tea, json("categoryId", hotDrinks, "name", "Tea", "type", "drink", "price", "4.20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.vatRate").value("0.0"));
	}

	private ResultActions postItem(String body) throws Exception {
		return mvc.perform(post("/admin/items")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions update(long id, String body) throws Exception {
		return mvc.perform(put("/admin/items/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}


	private ResultActions deleteItem(long id, String token) throws Exception {
		return mvc.perform(delete("/admin/items/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private String uploadImage(long id) throws Exception {
		String body = mvc.perform(multipart("/admin/items/" + id + "/image")
			.file(new MockMultipartFile("file", "a.png", "image/png", ImageUploadTest.noiseImage(64, 64, "png")))
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.imageUrl");
	}

	/** Sends an order with one line of the item as a waiter and returns the order id. */
	private long sendOrder(long itemId) throws Exception {
		createStaff("ben", Role.WAITER, "4712");
		String body = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, bearer(login("ben", "4712")))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableLabel\":\"12\",\"lines\":[" + json("menuItemId", itemId, "qty", 1) + "]}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

}
